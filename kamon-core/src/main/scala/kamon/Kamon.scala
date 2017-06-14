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

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.{Config, ConfigFactory}
import io.opentracing.propagation.Format
import io.opentracing.{ActiveSpan, Span, SpanContext}
import kamon.metric._
import kamon.trace.Tracer
import kamon.util.MeasurementUnit

import scala.concurrent.Future
import java.time.Duration

import io.opentracing.ActiveSpan.Continuation


object Kamon extends MetricLookup with ReporterRegistry with io.opentracing.Tracer {
  private val initialConfig = ConfigFactory.load()

  private val metricRegistry = new MetricRegistry(initialConfig)
  private val reporterRegistry = new ReporterRegistryImpl(metricRegistry, initialConfig)
  private val env = new AtomicReference[Environment](Environment.fromConfig(ConfigFactory.load()))
  private val kamonTracer = new Tracer(Kamon, reporterRegistry, initialConfig)

  def environment: Environment =
    env.get()

  def reconfigure(config: Config): Unit = synchronized {
    metricRegistry.reconfigure(config)
    reporterRegistry.reconfigure(config)
    env.set(Environment.fromConfig(config))
  }


  override def histogram(name: String, unit: MeasurementUnit, dynamicRange: Option[DynamicRange]): HistogramMetric =
    metricRegistry.histogram(name, unit, dynamicRange)

  override def counter(name: String, unit: MeasurementUnit): CounterMetric =
    metricRegistry.counter(name, unit)

  override def gauge(name: String, unit: MeasurementUnit): GaugeMetric =
    metricRegistry.gauge(name, unit)

  override def minMaxCounter(name: String, unit: MeasurementUnit, sampleInterval: Option[Duration],
      dynamicRange: Option[DynamicRange]): MinMaxCounterMetric =
    metricRegistry.minMaxCounter(name, unit, dynamicRange, sampleInterval)


  def tracer: Tracer =
    kamonTracer

  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    kamonTracer.buildSpan(operationName)

  override def extract[C](format: Format[C], carrier: C): SpanContext =
    kamonTracer.extract(format, carrier)

  override def inject[C](spanContext: SpanContext, format: Format[C], carrier: C): Unit =
    kamonTracer.inject(spanContext, format, carrier)

  override def activeSpan(): ActiveSpan =
    kamonTracer.activeSpan()

  override def makeActive(span: Span): ActiveSpan =
    kamonTracer.makeActive(span)


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
    reporterRegistry.loadReportersFromConfig()

  override def addReporter(reporter: MetricReporter): Registration =
    reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: MetricReporter, name: String): Registration =
    reporterRegistry.addReporter(reporter, name)

  override def addReporter(reporter: SpanReporter): Registration =
    reporterRegistry.addReporter(reporter)

  override def addReporter(reporter: SpanReporter, name: String): Registration =
    reporterRegistry.addReporter(reporter, name)

  override def stopAllReporters(): Future[Unit] =
    reporterRegistry.stopAllReporters()

}
