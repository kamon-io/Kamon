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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import kamon.{ RelativeNanoTimestamp, NanoInterval }
import kamon.metric.TraceMetrics.TraceMetricRecorder
import kamon.metric.{ MetricsExtension, TraceMetrics }

import scala.annotation.tailrec

private[trace] class MetricsOnlyContext(traceName: String, val token: String, izOpen: Boolean, val levelOfDetail: LevelOfDetail, val origin: TraceContextOrigin,
  val startRelativeTimestamp: RelativeNanoTimestamp, log: LoggingAdapter, metricsExtension: MetricsExtension, val system: ActorSystem)
    extends TraceContext {

  @volatile private var _name = traceName
  @volatile private var _isOpen = izOpen
  @volatile protected var _elapsedTime = NanoInterval.default

  private val _finishedSegments = new ConcurrentLinkedQueue[SegmentLatencyData]()
  private val _traceLocalStorage = new TraceLocalStorage

  def rename(newName: String): Unit =
    if (isOpen)
      _name = newName
    else if (log.isWarningEnabled)
      log.warning("Can't rename trace from [{}] to [{}] because the trace is already closed.", name, newName)

  def name: String = _name
  def isEmpty: Boolean = false
  def isOpen: Boolean = _isOpen
  def addMetadata(key: String, value: String): Unit = {}

  def finish(): Unit = {
    _isOpen = false
    val traceElapsedTime = NanoInterval.since(startRelativeTimestamp)
    _elapsedTime = traceElapsedTime

    val metricRecorder = metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory)
    metricRecorder.map { traceMetrics ⇒
      traceMetrics.elapsedTime.record(traceElapsedTime.nanos)
      drainFinishedSegments(traceMetrics)
    }
  }

  def startSegment(segmentName: String, category: String, library: String): Segment =
    new MetricsOnlySegment(segmentName, category, library)

  @tailrec private def drainFinishedSegments(metricRecorder: TraceMetricRecorder): Unit = {
    val segment = _finishedSegments.poll()
    if (segment != null) {
      metricRecorder.segmentRecorder(segment.identity).record(segment.duration.nanos)
      drainFinishedSegments(metricRecorder)
    }
  }

  protected def finishSegment(segmentName: String, category: String, library: String, duration: NanoInterval): Unit = {
    _finishedSegments.add(SegmentLatencyData(SegmentMetricIdentity(segmentName, category, library), duration))

    if (isClosed) {
      metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory).map { traceMetrics ⇒
        drainFinishedSegments(traceMetrics)
      }
    }
  }

  // Should only be used by the TraceLocal utilities.
  def traceLocalStorage: TraceLocalStorage = _traceLocalStorage

  // Handle with care and make sure that the trace is closed before calling this method, otherwise NanoInterval.default
  // will be returned.
  def elapsedTime: NanoInterval = _elapsedTime

  class MetricsOnlySegment(segmentName: String, val category: String, val library: String) extends Segment {
    private val _startTimestamp = RelativeNanoTimestamp.now
    @volatile private var _segmentName = segmentName
    @volatile private var _elapsedTime = NanoInterval.default
    @volatile private var _isOpen = true

    def name: String = _segmentName
    def isEmpty: Boolean = false
    def addMetadata(key: String, value: String): Unit = {}
    def isOpen: Boolean = _isOpen

    def rename(newName: String): Unit =
      if (isOpen)
        _segmentName = newName
      else if (log.isWarningEnabled)
        log.warning("Can't rename segment from [{}] to [{}] because the segment is already closed.", name, newName)

    def finish: Unit = {
      _isOpen = false
      val segmentElapsedTime = NanoInterval.since(_startTimestamp)
      _elapsedTime = segmentElapsedTime

      finishSegment(name, category, library, segmentElapsedTime)
    }

    // Handle with care and make sure that the segment is closed before calling this method, otherwise
    // NanoInterval.default will be returned.
    def elapsedTime: NanoInterval = _elapsedTime
    def startTimestamp: RelativeNanoTimestamp = _startTimestamp
  }
}