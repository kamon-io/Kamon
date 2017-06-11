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


object Kamon extends MetricLookup with ReporterRegistry with io.opentracing.Tracer {
  private val initialConfig = ConfigFactory.load()

  private val metricRegistry = new MetricRegistry(initialConfig)
  private val reporterRegistry = new ReporterRegistryImpl(metricRegistry, initialConfig)
  private val tracer = new Tracer(Kamon, reporterRegistry, initialConfig)
  private val env = new AtomicReference[Environment](Environment.fromConfig(ConfigFactory.load()))

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



  override def buildSpan(operationName: String): io.opentracing.Tracer.SpanBuilder =
    tracer.buildSpan(operationName)

  override def extract[C](format: Format[C], carrier: C): SpanContext =
    tracer.extract(format, carrier)

  override def inject[C](spanContext: SpanContext, format: Format[C], carrier: C): Unit =
    tracer.inject(spanContext, format, carrier)

  override def activeSpan(): ActiveSpan =
    tracer.activeSpan()

  override def makeActive(span: Span): ActiveSpan =
    tracer.makeActive(span)



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
