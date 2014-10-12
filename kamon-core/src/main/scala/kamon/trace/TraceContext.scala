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
import kamon.trace.TraceContext.SegmentIdentity
import kamon.metric.TraceMetrics.TraceMetricRecorder

import scala.annotation.tailrec

trait TraceContext {
  def name: String
  def token: String
  def system: ActorSystem
  def rename(name: String): Unit
  def levelOfDetail: TracingLevelOfDetail
  def startSegment(identity: SegmentIdentity, metadata: Map[String, String]): SegmentCompletionHandle
  def finish(metadata: Map[String, String])
  def origin: TraceContextOrigin
  def startMilliTime: Long
  def isOpen: Boolean

  private[kamon] val traceLocalStorage: TraceLocalStorage = new TraceLocalStorage
}

object TraceContext {
  type SegmentIdentity = MetricIdentity
}

trait SegmentCompletionHandle {
  def finish(metadata: Map[String, String] = Map.empty)
}

case class SegmentData(identity: MetricIdentity, duration: Long, metadata: Map[String, String])

sealed trait TracingLevelOfDetail
case object OnlyMetrics extends TracingLevelOfDetail
case object SimpleTrace extends TracingLevelOfDetail
case object FullTrace extends TracingLevelOfDetail

sealed trait TraceContextOrigin
object TraceContextOrigin {
  case object Local extends TraceContextOrigin
  case object Remote extends TraceContextOrigin
}

trait TraceContextAware extends Serializable {
  def captureNanoTime: Long
  def traceContext: Option[TraceContext]
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

trait SegmentCompletionHandleAware extends TraceContextAware {
  @volatile var segmentCompletionHandle: Option[SegmentCompletionHandle] = None
}

object SegmentCompletionHandleAware {
  def default: SegmentCompletionHandleAware = new DefaultSegmentCompletionHandleAware

  class DefaultSegmentCompletionHandleAware extends DefaultTraceContextAware with SegmentCompletionHandleAware {}
}

class SimpleMetricCollectionContext(traceName: String, val token: String, metadata: Map[String, String],
    val origin: TraceContextOrigin, val system: ActorSystem, val startMilliTime: Long = System.currentTimeMillis,
    izOpen: Boolean = true) extends TraceContext {

  @volatile private var _name = traceName
  @volatile private var _isOpen = izOpen

  val levelOfDetail = OnlyMetrics
  val startNanoTime = System.nanoTime()
  val finishedSegments = new ConcurrentLinkedQueue[SegmentData]()
  val metricsExtension = Kamon(Metrics)(system)

  def name: String = _name

  def rename(newName: String): Unit = _name = newName

  def isOpen(): Boolean = _isOpen

  def finish(metadata: Map[String, String]): Unit = {
    _isOpen = false

    val elapsedNanoTime =
      if (origin == TraceContextOrigin.Local)
        // Everything is local, nanoTime is still the best resolution we can use.
        System.nanoTime() - startNanoTime
      else
        // For a remote TraceContext we can only rely on the startMilliTime and we need to scale it to nanoseconds
        // to be consistent with unit used for all latency measurements.
        (System.currentTimeMillis() - startMilliTime) * 1000000L

    val metricRecorder = metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory)

    metricRecorder.map { traceMetrics ⇒
      traceMetrics.elapsedTime.record(elapsedNanoTime)
      drainFinishedSegments(traceMetrics)
    }
  }

  @tailrec private def drainFinishedSegments(metricRecorder: TraceMetricRecorder): Unit = {
    val segment = finishedSegments.poll()
    if(segment != null) {
      metricRecorder.segmentRecorder(segment.identity).record(segment.duration)
      println("CLOSED")
      drainFinishedSegments(metricRecorder)
    }
  }

  private def finishSegment(identity: MetricIdentity, duration: Long, metadata: Map[String, String]): Unit = {
    finishedSegments.add(SegmentData(identity, duration, metadata))

    if (!_isOpen) {
      metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory).map { traceMetrics ⇒
        drainFinishedSegments(traceMetrics)
      }
    }
  }

  def startSegment(identity: SegmentIdentity, metadata: Map[String, String]): SegmentCompletionHandle =
    new SimpleMetricCollectionCompletionHandle(identity, metadata)

  class SimpleMetricCollectionCompletionHandle(identity: MetricIdentity, startMetadata: Map[String, String]) extends SegmentCompletionHandle {
    val segmentStartNanoTime = System.nanoTime()

    def finish(metadata: Map[String, String] = Map.empty): Unit = {
      val segmentFinishNanoTime = System.nanoTime()
      finishSegment(identity, (segmentFinishNanoTime - segmentStartNanoTime), startMetadata ++ metadata)
    }
  }
}

