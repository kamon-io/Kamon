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

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.metrics._
import java.util.concurrent.ConcurrentLinkedQueue
import kamon.trace.TraceContextAware.DefaultTraceContextAware
import kamon.trace.TraceContext.SegmentIdentity
import kamon.trace.SegmentData

trait TraceContext {
  def name: String
  def token: String
  def system: ActorSystem
  def rename(name: String): Unit
  def levelOfDetail: TracingLevelOfDetail
  def startSegment(identity: SegmentIdentity, metadata: Map[String, String]): SegmentCompletionHandle
  def finish(metadata: Map[String, String])
}

object TraceContext {
  type SegmentIdentity = MetricIdentity
}

trait SegmentCompletionHandle {
  def finish(metadata: Map[String, String])
}

case class SegmentData(identity: MetricIdentity, duration: Long, metadata: Map[String, String])

sealed trait TracingLevelOfDetail
case object OnlyMetrics extends TracingLevelOfDetail
case object SimpleTrace extends TracingLevelOfDetail
case object FullTrace extends TracingLevelOfDetail

trait TraceContextAware {
  def captureMark: Long
  def traceContext: Option[TraceContext]
}

object TraceContextAware {
  def default: TraceContextAware = new DefaultTraceContextAware

  class DefaultTraceContextAware extends TraceContextAware {
    val captureMark = System.nanoTime()
    val traceContext = TraceRecorder.currentContext
  }
}

trait SegmentCompletionHandleAware extends TraceContextAware {
  @volatile var segmentCompletionHandle: Option[SegmentCompletionHandle] = None
}

object SegmentCompletionHandleAware {
  def default: SegmentCompletionHandleAware = new DefaultSegmentCompletionHandleAware

  class DefaultSegmentCompletionHandleAware extends DefaultTraceContextAware with SegmentCompletionHandleAware {}
}

class SimpleMetricCollectionContext(@volatile private var _name: String, val token: String, metadata: Map[String, String],
                                    val system: ActorSystem) extends TraceContext {
  @volatile private var _isOpen = true
  val levelOfDetail = OnlyMetrics
  val startMark = System.nanoTime()
  val finishedSegments = new ConcurrentLinkedQueue[SegmentData]()
  val metricsExtension = Kamon(Metrics)(system)

  def name: String = _name

  def rename(newName: String): Unit = _name = newName

  def isOpen(): Boolean = _isOpen

  def finish(metadata: Map[String, String]): Unit = {
    _isOpen = false
    val finishMark = System.nanoTime()
    val metricRecorder = metricsExtension.register(TraceMetrics(name), TraceMetrics.Factory)

    metricRecorder.map { traceMetrics ⇒
      traceMetrics.elapsedTime.record(finishMark - startMark)
      drainFinishedSegments(traceMetrics)
    }
  }

  private def drainFinishedSegments(metricRecorder: MetricMultiGroupRecorder): Unit = {
    while (!finishedSegments.isEmpty) {
      val segmentData = finishedSegments.poll()
      metricRecorder.record(segmentData.identity, segmentData.duration)
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
    val segmentStartMark = System.nanoTime()

    def finish(metadata: Map[String, String] = Map.empty): Unit = {
      val segmentFinishMark = System.nanoTime()
      finishSegment(identity, (segmentFinishMark - segmentStartMark), startMetadata ++ metadata)
    }
  }
}

