/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

private[kamon] class MetricsOnlyContext(traceName: String,
    val token: String,
    traceTags: Map[String, String],
    currentStatus: Status,
    val levelOfDetail: LevelOfDetail,
    val startTimestamp: RelativeNanoTimestamp,
    log: LoggingAdapter) extends TraceContext {

  @volatile private var _name = traceName
  @volatile private var _status = currentStatus
  @volatile protected var _elapsedTime = NanoInterval.default

  private val _finishedSegments = new ConcurrentLinkedQueue[SegmentLatencyData]()
  private val _traceLocalStorage = new TraceLocalStorage
  private val _tags = TrieMap.empty[String, String] ++= traceTags

  def rename(newName: String): Unit =
    if (Status.Open == status)
      _name = newName
    else
      log.warning("Can't rename trace from [{}] to [{}] because the trace is already closed.", name, newName)

  def name: String = _name
  def tags: Map[String, String] = _tags.toMap
  def isEmpty: Boolean = false
  def status: Status = _status
  def addMetadata(key: String, value: String): Unit = {}
  def addTag(key: String, value: String): Unit = _tags.put(key, value)
  def removeTag(key: String, value: String): Boolean = _tags.remove(key, value)

  private def finish(withError: Boolean): Unit = {
    _status = if (withError) Status.FinishedWithError else Status.FinishedSuccessfully
    val traceElapsedTime = NanoInterval.since(startTimestamp)
    _elapsedTime = traceElapsedTime

    if (Kamon.metrics.shouldTrack(name, TraceMetrics.category)) {
      val traceEntity = Kamon.metrics.entity(TraceMetrics, name, _tags.toMap)
      traceEntity.elapsedTime.record(traceElapsedTime.nanos)
      if (withError) traceEntity.errors.increment()
    }
    drainFinishedSegments()
  }

  def finish(): Unit = finish(withError = false)

  def finishWithError(cause: Throwable): Unit = {
    //we should do something with the Throwable in a near future
    finish(withError = true)
  }

  def startSegment(segmentName: String, category: String, library: String): Segment =
    startSegment(segmentName, category, library, Map.empty[String, String])

  def startSegment(segmentName: String, category: String, library: String, tags: Map[String, String]): Segment =
    new MetricsOnlySegment(segmentName, category, library, tags)

  @tailrec private def drainFinishedSegments(): Unit = {
    val segment = _finishedSegments.poll()
    if (segment != null) {
      val defaultTags = Map(
        "trace" -> name,
        "category" -> segment.category,
        "library" -> segment.library)

      if (Kamon.metrics.shouldTrack(segment.name, SegmentMetrics.category)) {
        val segmentEntity = Kamon.metrics.entity(SegmentMetrics, segment.name, defaultTags ++ segment.tags)
        segmentEntity.elapsedTime.record(segment.duration.nanos)
        if (segment.isFinishedWithError) segmentEntity.errors.increment()
      }
      drainFinishedSegments()
    }
  }

  protected def finishSegment(segmentName: String,
    category: String,
    library: String,
    duration: NanoInterval,
    segmentTags: Map[String, String],
    isFinishedWithError: Boolean): Unit = {

    _finishedSegments.add(SegmentLatencyData(segmentName, category, library, duration, segmentTags, isFinishedWithError))

    if (isClosed) {
      drainFinishedSegments()
    }
  }

  // Should only be used by the TraceLocal utilities.
  def traceLocalStorage: TraceLocalStorage = _traceLocalStorage

  // Handle with care and make sure that the trace is closed before calling this method, otherwise NanoInterval.default
  // will be returned.
  def elapsedTime: NanoInterval = _elapsedTime

  class MetricsOnlySegment(segmentName: String,
      val category: String,
      val library: String,
      segmentTags: Map[String, String]) extends Segment {

    private val _startTimestamp = RelativeNanoTimestamp.now
    private val _tags = TrieMap.empty[String, String] ++= segmentTags

    @volatile private var _segmentName = segmentName
    @volatile private var _elapsedTime = NanoInterval.default
    @volatile private var _status: Status = Status.Open

    def name: String = _segmentName
    def tags: Map[String, String] = _tags.toMap
    def isEmpty: Boolean = false
    def status: Status = _status
    def addMetadata(key: String, value: String): Unit = {}
    def addTag(key: String, value: String): Unit = _tags.put(key, value)
    def removeTag(key: String, value: String): Boolean = _tags.remove(key, value)

    def rename(newName: String): Unit =
      if (Status.Open == status)
        _segmentName = newName
      else
        log.warning("Can't rename segment from [{}] to [{}] because the segment is already closed.", name, newName)

    private def finish(withError: Boolean): Unit = {
      _status = if (withError) Status.FinishedWithError else Status.FinishedSuccessfully
      val segmentElapsedTime = NanoInterval.since(_startTimestamp)
      _elapsedTime = segmentElapsedTime

      finishSegment(name, category, library, segmentElapsedTime, _tags.toMap, withError)
    }

    def finishWithError(cause: Throwable): Unit = {
      //we should do something with the Throwable in a near future
      finish(withError = true)
    }

    def finish(): Unit = finish(withError = false)

    // Handle with care and make sure that the segment is closed before calling this method, otherwise
    // NanoInterval.default will be returned.
    def elapsedTime: NanoInterval = _elapsedTime
    def startTimestamp: RelativeNanoTimestamp = _startTimestamp

  }
}

case class SegmentLatencyData(name: String,
  category: String,
  library: String,
  duration: NanoInterval,
  tags: Map[String, String],
  isFinishedWithError: Boolean)