/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import com.typesafe.config.Config
import kamon.metric.Metrics
import kamon.util._

import scala.util.Try

trait Tracer {
  def newContext(name: String): TraceContext
  def newContext(name: String, token: String): TraceContext
  def newContext(name: String, token: String, timestamp: RelativeNanoTimestamp, isOpen: Boolean, isLocal: Boolean): TraceContext

  def subscribe(subscriber: ActorRef): Unit
  def unsubscribe(subscriber: ActorRef): Unit
}

object Tracer {
  private[kamon] val _traceContextStorage = new ThreadLocal[TraceContext] {
    override def initialValue(): TraceContext = EmptyTraceContext
  }

  def currentContext: TraceContext =
    _traceContextStorage.get()

  def setCurrentContext(context: TraceContext): Unit =
    _traceContextStorage.set(context)

  def clearCurrentContext: Unit =
    _traceContextStorage.remove()

  def withContext[T](context: TraceContext)(code: ⇒ T): T = {
    val oldContext = _traceContextStorage.get()
    _traceContextStorage.set(context)

    try code finally _traceContextStorage.set(oldContext)
  }
}

private[kamon] class TracerImpl(metricsExtension: Metrics, config: Config) extends Tracer {
  private val _settings = TraceSettings(config)
  private val _hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")
  private val _tokenCounter = new AtomicLong

  private val _subscriptions = new LazyActorRef
  private val _incubator = new LazyActorRef

  private def newToken: String =
    _hostnamePrefix + "-" + String.valueOf(_tokenCounter.incrementAndGet())

  def newContext(name: String): TraceContext =
    createTraceContext(name)

  def newContext(name: String, token: String): TraceContext =
    createTraceContext(name, token)

  def newContext(name: String, token: String, timestamp: RelativeNanoTimestamp, isOpen: Boolean, isLocal: Boolean): TraceContext =
    createTraceContext(name, token, timestamp, isOpen, isLocal)

  private def createTraceContext(traceName: String, token: String = newToken, startTimestamp: RelativeNanoTimestamp = RelativeNanoTimestamp.now,
    isOpen: Boolean = true, isLocal: Boolean = true): TraceContext = {

    def newMetricsOnlyContext = new MetricsOnlyContext(traceName, token, isOpen, _settings.levelOfDetail, startTimestamp, null)

    if (_settings.levelOfDetail == LevelOfDetail.MetricsOnly || !isLocal)
      newMetricsOnlyContext
    else {
      if (!_settings.sampler.shouldTrace)
        newMetricsOnlyContext
      else
        new TracingContext(traceName, token, true, _settings.levelOfDetail, isLocal, startTimestamp, null, dispatchTracingContext)
    }
  }

  def subscribe(subscriber: ActorRef): Unit =
    _subscriptions.tell(TraceSubscriptions.Subscribe(subscriber))

  def unsubscribe(subscriber: ActorRef): Unit =
    _subscriptions.tell(TraceSubscriptions.Unsubscribe(subscriber))

  private[kamon] def dispatchTracingContext(trace: TracingContext): Unit =
    if (_settings.sampler.shouldReport(trace.elapsedTime))
      if (trace.shouldIncubate)
        _incubator.tell(trace)
      else
        _subscriptions.tell(trace.generateTraceInfo)

  /**
   *  Tracer Extension initialization.
   */
  private var _system: ActorSystem = null
  private lazy val _start = {
    val subscriptions = _system.actorOf(Props[TraceSubscriptions], "trace-subscriptions")
    _subscriptions.point(subscriptions)
    _incubator.point(_system.actorOf(Incubator.props(subscriptions)))
  }

  def start(system: ActorSystem): Unit = synchronized {
    _system = system
    _start
    _system = null
  }
}

private[kamon] object TracerImpl {

  def apply(metricsExtension: Metrics, config: Config) =
    new TracerImpl(metricsExtension, config)
}

case class TraceInfo(name: String, token: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String], segments: List[SegmentInfo])
case class SegmentInfo(name: String, category: String, library: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String])