/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
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

package kamon

import java.time.Duration
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor}

import com.typesafe.config.{Config, ConfigFactory}
import kamon.context.{Codecs, Context, Key, Storage}
import kamon.metric._
import kamon.trace._
import kamon.util.{Clock, Filters, Matcher, Registration}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.Try


object Kamon extends MetricLookup with ReporterRegistry with Tracer {
  private val logger = LoggerFactory.getLogger("kamon.Kamon")

  @volatile private var _config = loadInitialConfiguration()
  @volatile private var _environment = Environment.fromConfig(_config)
  @volatile private var _filters = Filters.fromConfig(_config)

  private lazy val _clock = new Clock.Default()
  private lazy val _scheduler = Executors.newScheduledThreadPool(schedulerPoolSize(_config), numberedThreadFactory("kamon-scheduler", daemon = true))
  private lazy val _metrics = new MetricRegistry(_config, _scheduler)
  private lazy val _reporterRegistry = new ReporterRegistry.Default(_metrics, _config, _clock)
  private lazy val _tracer = Tracer.Default(Kamon, _reporterRegistry, _config, _clock)
  private lazy val _contextStorage = Storage.ThreadLocal()
  private lazy val _contextCodec = new Codecs(_config)
  private var _onReconfigureHooks = Seq.empty[OnReconfigureHook]

  sys.addShutdownHook(() => _scheduler.shutdown())

  def environment: Environment =
    _environment

  def config(): Config =
    _config

  def reconfigure(config: Config): Unit = synchronized {
    _config = config
    _environment = Environment.fromConfig(config)
    _filters = Filters.fromConfig(config)
    _metrics.reconfigure(config)
    _reporterRegistry.reconfigure(config)
    _tracer.reconfigure(config)
    _contextCodec.reconfigure(config)

    _onReconfigureHooks.foreach(hook => {
      Try(hook.onReconfigure(config)).failed.foreach(error =>
        logger.error("Exception occurred while trying to run a OnReconfigureHook", error)
      )
    })

    _scheduler match {
      case stpe: ScheduledThreadPoolExecutor => stpe.setCorePoolSize(schedulerPoolSize(config))
      case other => logger.error("Unexpected scheduler [{}] found when reconfiguring Kamon.", other)
    }
  }


  override def histogram(name: String, unit: MeasurementUnit, dynamicRange: Option[DynamicRange]): HistogramMetric =
    _metrics.histogram(name, unit, dynamicRange)

  override def counter(name: String, unit: MeasurementUnit): CounterMetric =
    _metrics.counter(name, unit)

  override def gauge(name: String, unit: MeasurementUnit): GaugeMetric =
    _metrics.gauge(name, unit)

  override def rangeSampler(name: String, unit: MeasurementUnit, sampleInterval: Option[Duration],
      dynamicRange: Option[DynamicRange]): RangeSamplerMetric =
    _metrics.rangeSampler(name, unit, dynamicRange, sampleInterval)

  override def timer(name: String, dynamicRange: Option[DynamicRange]): TimerMetric =
    _metrics.timer(name, dynamicRange)


  def tracer: Tracer =
    _tracer

  override def buildSpan(operationName: String): Tracer.SpanBuilder =
    _tracer.buildSpan(operationName)


  override def identityProvider: IdentityProvider =
    _tracer.identityProvider

  def contextCodec(): Codecs =
      _contextCodec

  def currentContext(): Context =
    _contextStorage.current()

  def currentSpan(): Span =
    _contextStorage.current().get(Span.ContextKey)

  def storeContext(context: Context): Storage.Scope =
    _contextStorage.store(context)

  def withContext[T](context: Context)(f: => T): T = {
    val scope = _contextStorage.store(context)
    try {
      f
    } finally {
      scope.close()
    }
  }

  def withContextKey[T, K](key: Key[K], value: K)(f: => T): T =
    withContext(currentContext().withKey(key, value))(f)

  def withSpan[T](span: Span)(f: => T): T =
    withSpan(span, true)(f)

  def withSpan[T](span: Span, finishSpan: Boolean)(f: => T): T = {
    try {
      withContextKey(Span.ContextKey, span)(f)
    } catch {
      case t: Throwable =>
        span.addError(t.getMessage, t)
        throw t

    } finally {
      if(finishSpan)
        span.finish()
    }
  }

  override def loadReportersFromConfig(): Unit =
    _reporterRegistry.loadReportersFromConfig()

  override def addReporter(reporter: MetricReporter): Registration =
    _reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: MetricReporter, name: String): Registration =
    _reporterRegistry.addReporter(reporter, name)

  override def addReporter(reporter: MetricReporter, name: String, filter: String): Registration =
    _reporterRegistry.addReporter(reporter, name, filter)

  override def addReporter(reporter: SpanReporter): Registration =
    _reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: SpanReporter, name: String): Registration =
    _reporterRegistry.addReporter(reporter, name)

  override def stopAllReporters(): Future[Unit] =
    _reporterRegistry.stopAllReporters()

  def filter(filterName: String, pattern: String): Boolean =
    _filters.accept(filterName, pattern)

  def filter(filterName: String): Matcher =
    _filters.get(filterName)

  def clock(): Clock =
    _clock

  /**
    * Register a reconfigure hook that will be run when the a call to Kamon.reconfigure(config) is performed. All
    * registered hooks will run sequentially in the same Thread that calls Kamon.reconfigure(config).
    */
  def onReconfigure(hook: OnReconfigureHook): Unit = synchronized {
    _onReconfigureHooks = hook +: _onReconfigureHooks
  }

  def scheduler(): ScheduledExecutorService =
    _scheduler

  private def schedulerPoolSize(config: Config): Int =
    config.getInt("kamon.scheduler-pool-size")

  private def loadInitialConfiguration(): Config = {
    Try {
      ConfigFactory.load(ConfigFactory.load())

    } recoverWith {
      case t: Throwable =>
        logger.warn("Failed to load the default configuration, attempting to load the reference configuration", t)

        Try {
          val referenceConfig = ConfigFactory.defaultReference()
          logger.warn("Initializing with the default reference configuration, none of the user settings might be in effect", t)
          referenceConfig
        }

    } recover {
      case t: Throwable =>
        logger.error("Failed to load the reference configuration, please check your reference.conf files for errors", t)
        ConfigFactory.empty()
    } get
  }

}

trait OnReconfigureHook {
  def onReconfigure(newConfig: Config): Unit
}
