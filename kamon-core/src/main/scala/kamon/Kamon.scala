/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import com.typesafe.config.{Config, ConfigFactory}
import io.opentracing.propagation.Format
import io.opentracing.{ActiveSpan, Span, SpanContext}
import kamon.metric._
import kamon.trace.Tracer
import kamon.util.{Filters, MeasurementUnit, Registration}

import scala.concurrent.Future
import java.time.Duration
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor}

import io.opentracing.ActiveSpan.Continuation
import org.slf4j.LoggerFactory

import scala.util.Try


object Kamon extends MetricLookup with ReporterRegistry with io.opentracing.Tracer {
  private val logger = LoggerFactory.getLogger("kamon.Kamon")
  @volatile private var _config = ConfigFactory.load()
  @volatile private var _environment = Environment.fromConfig(_config)
  @volatile private var _filters = Filters.fromConfig(_config)

  private val _scheduler = Executors.newScheduledThreadPool(schedulerPoolSize(_config), numberedThreadFactory("kamon-scheduler"))
  private val _metrics = new MetricRegistry(_config, _scheduler)
  private val _reporters = new ReporterRegistryImpl(_metrics, _config)
  private val _tracer = new Tracer(Kamon, _reporters, _config)
  private var _onReconfigureHooks = Seq.empty[OnReconfigureHook]

  def environment: Environment =
    _environment

  def config(): Config =
    _config

  def reconfigure(config: Config): Unit = synchronized {
    _config = config
    _environment = Environment.fromConfig(config)
    _filters = Filters.fromConfig(config)
    _metrics.reconfigure(config)
    _reporters.reconfigure(config)

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

  override def minMaxCounter(name: String, unit: MeasurementUnit, sampleInterval: Option[Duration],
      dynamicRange: Option[DynamicRange]): MinMaxCounterMetric =
    _metrics.minMaxCounter(name, unit, dynamicRange, sampleInterval)

  override def timer(name: String, dynamicRange: Option[DynamicRange]): TimerMetric =
    _metrics.timer(name, dynamicRange)


  def tracer: Tracer =
    _tracer

  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    _tracer.buildSpan(operationName)

  override def extract[C](format: Format[C], carrier: C): SpanContext =
    _tracer.extract(format, carrier)

  override def inject[C](spanContext: SpanContext, format: Format[C], carrier: C): Unit =
    _tracer.inject(spanContext, format, carrier)

  override def activeSpan(): ActiveSpan =
    _tracer.activeSpan()

  override def makeActive(span: Span): ActiveSpan =
    _tracer.makeActive(span)


  /**
    * Makes the provided Span active before code is evaluated and deactivates it afterwards.
    */
  def withSpan[T](span: Span)(code: => T): T = {
    val activeSpan = makeActive(span)
    val evaluatedCode = code
    activeSpan.deactivate()
    evaluatedCode
  }

  /**
    * Actives the provided Continuation before code is evaluated and deactivates it afterwards.
    */
  def withContinuation[T](continuation: Continuation)(code: => T): T = {
    if(continuation == null)
      code
    else {
      val activeSpan = continuation.activate()
      val evaluatedCode = code
      activeSpan.deactivate()
      evaluatedCode
    }
  }

  /**
    * Captures a continuation from the currently active Span (if any).
    */
  def activeSpanContinuation(): Continuation = {
    val activeSpan = Kamon.activeSpan()
    if(activeSpan == null)
      null
    else
      activeSpan.capture()
  }

  /**
    * Runs the provided closure with the currently active Span (if any).
    */
  def onActiveSpan[T](code: ActiveSpan => T): Unit = {
    val activeSpan = Kamon.activeSpan()
    if(activeSpan != null)
      code(activeSpan)
  }

  /**
    * Evaluates the provided closure with the currently active Span (if any) and returns the evaluation result. If there
    * was no active Span then the provided fallback value
    */
  def fromActiveSpan[T](code: ActiveSpan => T): Option[T] =
    Option(activeSpan()).map(code)


  override def loadReportersFromConfig(): Unit =
    _reporters.loadReportersFromConfig()

  override def addReporter(reporter: MetricReporter): Registration =
    _reporters.addReporter(reporter)

  override def addReporter(reporter: MetricReporter, name: String): Registration =
    _reporters.addReporter(reporter, name)

  override def addReporter(reporter: SpanReporter): Registration =
    _reporters.addReporter(reporter)

  override def addReporter(reporter: SpanReporter, name: String): Registration =
    _reporters.addReporter(reporter, name)

  override def stopAllReporters(): Future[Unit] =
    _reporters.stopAllReporters()

  def filter(filterName: String, pattern: String): Boolean =
    _filters.accept(filterName, pattern)

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

}

trait OnReconfigureHook {
  def onReconfigure(newConfig: Config): Unit
}
