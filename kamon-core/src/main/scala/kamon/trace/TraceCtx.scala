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

import akka.actor.{ExtendedActorSystem, ActorSystem}
import akka.dispatch.AbstractNodeQueue
import kamon.Kamon
import kamon.metrics.{TraceMetrics, Metrics}
import java.util.concurrent.atomic.AtomicLong

sealed trait TracingLevelOfDetail
case object OnlyMetrics extends TracingLevelOfDetail
case object SimpleTrace extends TracingLevelOfDetail
case object FullTrace extends TracingLevelOfDetail

trait TraceContext {
  def token: String
  def name: String
  def rename(newName: String): Unit
  def levelOfDetail: TracingLevelOfDetail
  def startSegment
  def finish(metadata: Map[String, String])

}

trait TraceContextAware {
  def captureMark: Long
  def traceContext: Option[TraceContext]
}

object TraceContextAware {
  def default: TraceContextAware = new TraceContextAware {
    val captureMark = System.nanoTime()
    val traceContext = TraceRecorder.currentContext
  }
}

object TraceContext {

}

class SimpleMetricCollectionContext(val token: String, @volatile private var _name: String, val system: ActorSystem,
                                    metadata: Map[String, String]) extends TraceContext {
  val levelOfDetail = OnlyMetrics

  @volatile private var _isOpen = true

  val startMark = System.nanoTime()

  def name: String = _name

  def rename(newName: String): Unit = _name = newName

  def isOpen(): Boolean = _isOpen

  def finish(metadata: Map[String, String]): Unit = {
    val finishMark = System.nanoTime()

    // Store all metrics!
    val metricsExtension = Kamon(Metrics)(system)
    val metricRecorder = metricsExtension.register(name, TraceMetrics)

    metricRecorder.map { traceMetrics =>
      traceMetrics.elapsedTime.record(finishMark - startMark)
    }

  }

  override def startSegment: Unit = ???


}

private[kamon] class SegmentRecordingQueue extends AbstractNodeQueue[String]




class TraceRecorder(system: ExtendedActorSystem) {

}

object TraceRecorder {
  private val tokenCounter = new AtomicLong
  private val traceContextStorage = new ThreadLocal[Option[TraceContext]] {
    override def initialValue(): Option[TraceContext] = None
  }

  private def newTraceContext(name: String, token: Option[String], metadata: Map[String, String], system: ActorSystem): TraceContext = ???

  def setContext(context: Option[TraceContext]): Unit = traceContextStorage.set(context)

  def clearContext: Unit = traceContextStorage.set(None)

  def currentContext: Option[TraceContext] = traceContextStorage.get()

  def start(name: String, token: Option[String] = None, metadata: Map[String, String] = Map.empty)(implicit system: ActorSystem) = {
    val ctx = newTraceContext(name, token, metadata, system)
    traceContextStorage.set(Some(ctx))
  }

  def withContext[T](context: Option[TraceContext])(thunk: => T): T = {
    val oldContext = currentContext
    setContext(context)

    try thunk finally setContext(oldContext)
  }

  def finish(metadata: Map[String, String] = Map.empty): Unit = currentContext.map(_.finish(metadata))

}
