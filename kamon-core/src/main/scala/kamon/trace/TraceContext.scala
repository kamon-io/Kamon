/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import java.io.ObjectStreamException

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.metric._
import java.util.concurrent.ConcurrentLinkedQueue
import kamon.trace.TraceContextAware.DefaultTraceContextAware
import kamon.metric.TraceMetrics.TraceMetricRecorder

import scala.annotation.tailrec

sealed trait TraceContext {
  def name: String
  def token: String
  def rename(name: String): Unit
  def finish(): Unit
  def origin: TraceContextOrigin
  def isOpen: Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty
  def startSegment(segmentName: String, label: String): Segment
  def nanoTimestamp: Long
}

sealed trait Segment {
  def name: String
  def rename(newName: String): Unit
  def label: String
  def finish(): Unit
  def isEmpty: Boolean
}

case object EmptyTraceContext extends TraceContext {
  def name: String = "empty-trace"
  def token: String = ""
  def rename(name: String): Unit = {}
  def finish(): Unit = {}
  def origin: TraceContextOrigin = TraceContextOrigin.Local
  def isOpen: Boolean = false
  def isEmpty: Boolean = true
  def startSegment(segmentName: String, label: String): Segment = EmptySegment
  def nanoTimestamp: Long = 0L

  case object EmptySegment extends Segment {
    val name: String = "empty-segment"
    val label: String = "empty-label"
    def isEmpty: Boolean = true
    def rename(newName: String): Unit = {}
    def finish: Unit = {}
  }
}

class DefaultTraceContext(traceName: String, val token: String, izOpen: Boolean, val levelOfDetail: LevelOfDetail,
    val origin: TraceContextOrigin, nanoTimeztamp: Long, val system: ActorSystem) extends TraceContext {

  val isEmpty: Boolean = false
  @volatile private var _name = traceName
  @volatile private var _isOpen = izOpen

  private val _nanoTimestamp = nanoTimeztamp
  private val finishedSegments = new ConcurrentLinkedQueue[SegmentData]()
  private val metricsExtension = Kamon(Metrics)(system)
  private[kamon] val traceLocalStorage: TraceLocalStorage = new TraceLocalStorage

  def name: String = _name
  def rename(newName: String): Unit =
    if (isOpen) _name = newName // TODO: log a warning about renaming a closed trace.

  def isOpen: Boolean = _isOpen
  def nanoTimestamp: Long = _nanoTimestamp

  def finish(): Unit = {
    _isOpen = false
    val elapsedNanoTime = System.nanoTime() - _nanoTimestamp
    val metricRecorder = metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory)

    metricRecorder.map { traceMetrics ⇒
      traceMetrics.elapsedTime.record(elapsedNanoTime)
      drainFinishedSegments(traceMetrics)
    }
  }

  def startSegment(segmentName: String, segmentLabel: String): Segment = new DefaultSegment(segmentName, segmentLabel)

  @tailrec private def drainFinishedSegments(metricRecorder: TraceMetricRecorder): Unit = {
    val segment = finishedSegments.poll()
    if (segment != null) {
      metricRecorder.segmentRecorder(segment.identity).record(segment.duration)
      drainFinishedSegments(metricRecorder)
    }
  }

  private def finishSegment(segmentName: String, label: String, duration: Long): Unit = {
    finishedSegments.add(SegmentData(SegmentMetricIdentity(segmentName, label), duration))

    if (!_isOpen) {
      metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory).map { traceMetrics ⇒
        drainFinishedSegments(traceMetrics)
      }
    }
  }

  class DefaultSegment(segmentName: String, val label: String) extends Segment {
    private val _segmentStartNanoTime = System.nanoTime()
    @volatile private var _segmentName = segmentName
    @volatile private var _isOpen = true

    def name: String = _segmentName
    def rename(newName: String): Unit = _segmentName = newName
    def isEmpty: Boolean = false

    def finish: Unit = {
      val segmentFinishNanoTime = System.nanoTime()
      finishSegment(segmentName, label, (segmentFinishNanoTime - _segmentStartNanoTime))
    }
  }
}

case class SegmentMetricIdentity(name: String, label: String) extends MetricIdentity
case class SegmentData(identity: SegmentMetricIdentity, duration: Long)

object SegmentMetricIdentityLabel {
  val HttpClient = "http-client"
}

sealed trait LevelOfDetail
object LevelOfDetail {
  case object OnlyMetrics extends LevelOfDetail
  case object SimpleTrace extends LevelOfDetail
  case object FullTrace extends LevelOfDetail
}

sealed trait TraceContextOrigin
object TraceContextOrigin {
  case object Local extends TraceContextOrigin
  case object Remote extends TraceContextOrigin
}

trait TraceContextAware extends Serializable {
  def captureNanoTime: Long
  def traceContext: TraceContext
}

object TraceContextAware {
  def default: TraceContextAware = new DefaultTraceContextAware

  class DefaultTraceContextAware extends TraceContextAware {
    @transient val captureNanoTime = System.nanoTime()
    @transient val traceContext = TraceRecorder.currentContext

    //
    // Beware of this hack, it might bite us in the future!
    //
    // When using remoting/cluster all messages carry the TraceContext in the envelope in which they
    // are sent but that doesn't apply to System Messages. We are certain that the TraceContext is
    // available (if any) when the system messages are read and this will make sure that it is correctly
    // captured and propagated.
    @throws[ObjectStreamException]
    private def readResolve: AnyRef = {
      new DefaultTraceContextAware
    }
  }
}

trait SegmentAware extends TraceContextAware {
  @volatile var segment: Segment = EmptyTraceContext.EmptySegment
}

object SegmentAware {
  def default: SegmentAware = new DefaultSegmentAware
  class DefaultSegmentAware extends DefaultTraceContextAware with SegmentAware {}
}