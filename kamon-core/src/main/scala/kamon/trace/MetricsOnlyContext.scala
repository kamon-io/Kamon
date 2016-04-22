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

import akka.event.LoggingAdapter
import kamon.Kamon
import kamon.metric.{ SegmentMetrics, TraceMetrics }
import kamon.util.{ NanoInterval, RelativeNanoTimestamp }

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap

private[kamon] class MetricsOnlyContext(traceName: String, val token: String, traceTags: Map[String, String], izOpen: Boolean, val levelOfDetail: LevelOfDetail,
  val startTimestamp: RelativeNanoTimestamp, log: LoggingAdapter)
    extends TraceContext {

  @volatile private var _name = traceName
  @volatile private var _isOpen = izOpen
  @volatile protected var _elapsedTime = NanoInterval.default

  private val _finishedSegments = new ConcurrentLinkedQueue[SegmentLatencyData]()
  private val _traceLocalStorage = new TraceLocalStorage
  private val _tags = TrieMap.empty[String, String] ++= traceTags

  def rename(newName: String): Unit =
    if (isOpen)
      _name = newName
    else
      log.warning("Can't rename trace from [{}] to [{}] because the trace is already closed.", name, newName)

  def name: String = _name
  def isEmpty: Boolean = false
  def isOpen: Boolean = _isOpen

  def addMetadata(key: String, value: String): Unit = {}

  def addTag(key: String, value: String): Unit = _tags.put(key, value)
  def removeTag(key: String, value: String): Boolean = _tags.remove(key, value)

  def finish(): Unit = {
    _isOpen = false
    val traceElapsedTime = NanoInterval.since(startTimestamp)
    _elapsedTime = traceElapsedTime

    if (Kamon.metrics.shouldTrack(name, TraceMetrics.category))
      Kamon.metrics.entity(TraceMetrics, name, _tags.toMap).elapsedTime.record(traceElapsedTime.nanos)
    drainFinishedSegments()
  }

  def startSegment(segmentName: String, category: String, library: String): Segment =
    startSegment(segmentName, category, library, Map.empty[String, String])

  def startSegment(segmentName: String, category: String, library: String, tags: Map[String, String]): Segment =
    new MetricsOnlySegment(segmentName, category, library, tags)

  @tailrec private def drainFinishedSegments(): Unit = {
    val segment = _finishedSegments.poll()
    if (segment != null) {
      val deefaultTags = Map(
        "trace" -> name,
        "category" -> segment.category,
        "library" -> segment.library)

      if (Kamon.metrics.shouldTrack(segment.name, SegmentMetrics.category))
        Kamon.metrics.entity(SegmentMetrics, segment.name, deefaultTags ++ segment.tags).elapsedTime.record(segment.duration.nanos)
      drainFinishedSegments()
    }
  }

  protected def finishSegment(segmentName: String, category: String, library: String, duration: NanoInterval, segmentTags: Map[String, String]): Unit = {
    _finishedSegments.add(SegmentLatencyData(segmentName, category, library, duration, segmentTags))

    if (isClosed) {
      drainFinishedSegments()
    }
  }

  // Should only be used by the TraceLocal utilities.
  def traceLocalStorage: TraceLocalStorage = _traceLocalStorage

  // Handle with care and make sure that the trace is closed before calling this method, otherwise NanoInterval.default
  // will be returned.
  def elapsedTime: NanoInterval = _elapsedTime

  class MetricsOnlySegment(segmentName: String, val category: String, val library: String, segmentTags: Map[String, String]) extends Segment {
    private val _startTimestamp = RelativeNanoTimestamp.now
    protected val _tags = TrieMap.empty[String, String] ++= segmentTags

    @volatile private var _segmentName = segmentName
    @volatile private var _elapsedTime = NanoInterval.default
    @volatile private var _isOpen = true

    def name: String = _segmentName
    def isEmpty: Boolean = false
    def isOpen: Boolean = _isOpen

    def addMetadata(key: String, value: String): Unit = {}

    def addTag(key: String, value: String): Unit = _tags.put(key, value)
    def removeTag(key: String, value: String): Boolean = _tags.remove(key, value)

    def rename(newName: String): Unit =
      if (isOpen)
        _segmentName = newName
      else
        log.warning("Can't rename segment from [{}] to [{}] because the segment is already closed.", name, newName)

    def finish: Unit = {
      _isOpen = false
      val segmentElapsedTime = NanoInterval.since(_startTimestamp)
      _elapsedTime = segmentElapsedTime

      finishSegment(name, category, library, segmentElapsedTime, _tags.toMap)
    }

    // Handle with care and make sure that the segment is closed before calling this method, otherwise
    // NanoInterval.default will be returned.
    def elapsedTime: NanoInterval = _elapsedTime
    def startTimestamp: RelativeNanoTimestamp = _startTimestamp
  }
}

case class SegmentLatencyData(name: String, category: String, library: String, duration: NanoInterval, tags: Map[String, String])
