/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import kamon.{ NanoInterval, NanoTimestamp, RelativeNanoTimestamp }
import kamon.metric.MetricsExtension

import scala.collection.concurrent.TrieMap

private[trace] class TracingContext(traceName: String, token: String, izOpen: Boolean, levelOfDetail: LevelOfDetail, origin: TraceContextOrigin,
  startTimeztamp: RelativeNanoTimestamp, log: LoggingAdapter, metricsExtension: MetricsExtension, traceExtension: TraceExtension, system: ActorSystem)
    extends MetricsOnlyContext(traceName, token, izOpen, levelOfDetail, origin, startTimeztamp, log, metricsExtension, system) {

  private val _openSegments = new AtomicInteger(0)
  private val _startTimestamp = NanoTimestamp.now
  private val _allSegments = new ConcurrentLinkedQueue[TracingSegment]()
  private val _metadata = TrieMap.empty[String, String]

  override def addMetadata(key: String, value: String): Unit = _metadata.put(key, value)

  override def startSegment(segmentName: String, category: String, library: String): Segment = {
    _openSegments.incrementAndGet()
    val newSegment = new TracingSegment(segmentName, category, library)
    _allSegments.add(newSegment)
    newSegment
  }

  override def finish(): Unit = {
    super.finish()
    traceExtension.dispatchTracingContext(this)
  }

  override def finishSegment(segmentName: String, category: String, library: String, duration: NanoInterval): Unit = {
    _openSegments.decrementAndGet()
    super.finishSegment(segmentName, category, library, duration)
  }

  def shouldIncubate: Boolean = isOpen || _openSegments.get() > 0

  // Handle with care, should only be used after a trace is finished.
  def generateTraceInfo: TraceInfo = {
    require(isClosed, "Can't generated a TraceInfo if the Trace has not closed yet.")

    val currentSegments = _allSegments.iterator()
    var segmentsInfo = List.newBuilder[SegmentInfo]

    while (currentSegments.hasNext()) {
      val segment = currentSegments.next()
      if (segment.isClosed)
        segmentsInfo += segment.createSegmentInfo(_startTimestamp, startRelativeTimestamp)
      else
        log.warning("Segment [{}] will be left out of TraceInfo because it was still open.", segment.name)
    }

    TraceInfo(name, token, _startTimestamp, elapsedTime, _metadata.toMap, segmentsInfo.result())
  }

  class TracingSegment(segmentName: String, category: String, library: String) extends MetricsOnlySegment(segmentName, category, library) {
    private val metadata = TrieMap.empty[String, String]
    override def addMetadata(key: String, value: String): Unit = metadata.put(key, value)

    // Handle with care, should only be used after the segment has finished.
    def createSegmentInfo(traceStartTimestamp: NanoTimestamp, traceRelativeTimestamp: RelativeNanoTimestamp): SegmentInfo = {
      require(isClosed, "Can't generated a SegmentInfo if the Segment has not closed yet.")

      // We don't have a epoch-based timestamp for the segments because calling System.currentTimeMillis() is both
      // expensive and inaccurate, but we can do that once for the trace and calculate all the segments relative to it.
      val segmentStartTimestamp = new NanoTimestamp((this.startTimestamp.nanos - traceRelativeTimestamp.nanos) + traceStartTimestamp.nanos)

      SegmentInfo(this.name, category, library, segmentStartTimestamp, this.elapsedTime, metadata.toMap)
    }
  }
}