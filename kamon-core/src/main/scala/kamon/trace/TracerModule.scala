/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

import java.util
import java.util.function.Supplier

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.MetricsModule
import kamon.util._

import scala.collection.JavaConverters._

trait TracerModule {
  def newContext(name: String): TraceContext
  def newContext(name: String, token: Option[String]): TraceContext
  def newContext(name: String, token: Option[String], tags: Map[String, String]): TraceContext
  def newContext(name: String, token: Option[String], tags: Map[String, String], timestamp: RelativeNanoTimestamp, state: Status, isLocal: Boolean): TraceContext

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

  // Java variant.
  def withContext[T](context: TraceContext, code: Supplier[T]): T =
    withContext(context)(code.get)

  def withNewContext[T](traceName: String, traceToken: Option[String], tags: Map[String, String], autoFinish: Boolean)(code: ⇒ T): T = {
    withContext(Kamon.tracer.newContext(traceName, traceToken, tags)) {
      val codeResult = code
      if (autoFinish)
        currentContext.finish()

      codeResult
    }
  }

  def withNewContext[T](traceName: String)(code: ⇒ T): T =
    withNewContext(traceName, None)(code)

  def withNewContext[T](traceName: String, tags: Map[String, String])(code: ⇒ T): T =
    withNewContext(traceName, None, tags)(code)

  def withNewContext[T](traceName: String, traceToken: Option[String])(code: ⇒ T): T =
    withNewContext(traceName, traceToken, Map.empty[String, String])(code)

  def withNewContext[T](traceName: String, traceToken: Option[String], tags: Map[String, String])(code: ⇒ T): T =
    withNewContext(traceName, traceToken, tags, autoFinish = false)(code)

  def withNewContext[T](traceName: String, autoFinish: Boolean)(code: ⇒ T): T =
    withNewContext(traceName, None, Map.empty[String, String], autoFinish)(code)

  def withNewContext[T](traceName: String, tags: Map[String, String], autoFinish: Boolean)(code: ⇒ T): T =
    withNewContext(traceName, None, tags, autoFinish)(code)

  // Java variants.
  def withNewContext[T](traceName: String, traceToken: Option[String], autoFinish: Boolean, code: Supplier[T]): T =
    withNewContext(traceName, traceToken, Map.empty[String, String], autoFinish)(code.get)

  def withNewContext[T](traceName: String, traceToken: Option[String], tags: util.Map[String, String], autoFinish: Boolean, code: Supplier[T]): T =
    withNewContext(traceName, traceToken, tags.asScala.toMap, autoFinish)(code.get)

  def withNewContext[T](traceName: String, code: Supplier[T]): T =
    withNewContext(traceName, None, Map.empty[String, String], autoFinish = false)(code.get)

  def withNewContext[T](traceName: String, tags: util.Map[String, String], code: Supplier[T]): T =
    withNewContext(traceName, None, tags.asScala.toMap, autoFinish = false)(code.get)

  def withNewContext[T](traceName: String, traceToken: Option[String], code: Supplier[T]): T =
    withNewContext(traceName, traceToken, Map.empty[String, String], autoFinish = false)(code.get)

  def withNewContext[T](traceName: String, traceToken: Option[String], tags: util.Map[String, String], code: Supplier[T]): T =
    withNewContext(traceName, traceToken, tags.asScala.toMap, autoFinish = false)(code.get)

  def withNewContext[T](traceName: String, autoFinish: Boolean, code: Supplier[T]): T =
    withNewContext(traceName, None, Map.empty[String, String], autoFinish)(code.get)

  def withNewContext[T](traceName: String, tags: util.Map[String, String], autoFinish: Boolean, code: Supplier[T]): T =
    withNewContext(traceName, None, tags.asScala.toMap, autoFinish)(code.get)
}

private[kamon] class TracerModuleImpl(metricsExtension: MetricsModule, config: Config) extends TracerModule {
  private val _settings = TraceSettings(config)

  private val _subscriptions = new LazyActorRef
  private val _incubator = new LazyActorRef
  private val _dynamic = new akka.actor.ReflectiveDynamicAccess(getClass.getClassLoader)
  private val _tokenGenerator = _dynamic.createInstanceFor[() ⇒ String](_settings.tokenGeneratorFQN, Nil).get // let's bubble up any problems.

  private def newToken: String = _tokenGenerator()

  def newContext(name: String): TraceContext =
    createTraceContext(name, None)

  def newContext(name: String, token: Option[String]): TraceContext =
    createTraceContext(name, token)

  def newContext(name: String, token: Option[String], tags: Map[String, String]): TraceContext =
    createTraceContext(name, token, tags)

  def newContext(name: String, token: Option[String], tags: Map[String, String], timestamp: RelativeNanoTimestamp, status: Status, isLocal: Boolean): TraceContext =
    createTraceContext(name, token, tags, timestamp, status, isLocal)

  private def createTraceContext(traceName: String, token: Option[String], tags: Map[String, String] = Map.empty, startTimestamp: RelativeNanoTimestamp = RelativeNanoTimestamp.now,
    status: Status = Status.Open, isLocal: Boolean = true): TraceContext = {

    def newMetricsOnlyContext(token: String): TraceContext =
      new MetricsOnlyContext(traceName, token, tags, status, _settings.levelOfDetail, startTimestamp, _logger)

    val traceToken = token.getOrElse(newToken)

    _settings.levelOfDetail match {
      case LevelOfDetail.MetricsOnly ⇒
        newMetricsOnlyContext(traceToken)
      case _ if !isLocal || !_settings.sampler.shouldTrace ⇒
        newMetricsOnlyContext(traceToken)
      case _ ⇒
        new TracingContext(traceName, traceToken, tags, currentStatus = Status.Open, _settings.levelOfDetail, isLocal, startTimestamp, _logger, dispatchTracingContext)
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
  private var _logger: LoggingAdapter = null
  private lazy val _start = {
    val subscriptions = _system.actorOf(Props[TraceSubscriptions], "trace-subscriptions")
    _subscriptions.point(subscriptions)
    _incubator.point(_system.actorOf(Incubator.props(subscriptions)))
  }

  def start(system: ActorSystem): Unit = synchronized {
    _system = system
    _logger = Logging(_system, "TracerModule")
    _start
    _system = null
  }
}

private[kamon] object TracerModuleImpl {

  def apply(metricsExtension: MetricsModule, config: Config) =
    new TracerModuleImpl(metricsExtension, config)
}

case class TraceInfo(name: String, token: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String], tags: Map[String, String], segments: List[SegmentInfo], status: Status)
case class SegmentInfo(name: String, category: String, library: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String], tags: Map[String, String], status: Status)