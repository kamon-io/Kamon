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

import java.time.Duration
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor}

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric._
import kamon.trace._
import kamon.util.{Clock, Filters, Matcher, Registration}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.Try


object Kamon extends MetricLookup with ClassLoading with Configuration with ReporterRegistry with Tracer with ContextPropagation with ContextStorage {
  private val logger = LoggerFactory.getLogger("kamon.Kamon")

  @volatile private var _config = ConfigFactory.load()
  @volatile private var _environment = Environment.fromConfig(_config)
  @volatile private var _filters = Filters.fromConfig(_config)

  private val _clock = new Clock.Default()
  private val _scheduler = Executors.newScheduledThreadPool(schedulerPoolSize(_config), numberedThreadFactory("kamon-scheduler", daemon = true))
  private val _metrics = new MetricRegistry(_config, _scheduler)
  private val _reporterRegistry = new ReporterRegistry.Default(_metrics, _config, _clock)
  private val _tracer = Tracer.Default(Kamon, _reporterRegistry, _config, _clock)
  //private var _onReconfigureHooks = Seq.empty[OnReconfigureHook]

  sys.addShutdownHook(() => _scheduler.shutdown())

  def environment: Environment =
    _environment

  onReconfigure(newConfig => {
    _config = config
    _environment = Environment.fromConfig(config)
    _filters = Filters.fromConfig(config)
    _metrics.reconfigure(config)
    _reporterRegistry.reconfigure(config)
    _tracer.reconfigure(config)

    _scheduler match {
      case stpe: ScheduledThreadPoolExecutor => stpe.setCorePoolSize(schedulerPoolSize(config))
      case other => logger.error("Unexpected scheduler [{}] found when reconfiguring Kamon.", other)
    }
  })

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

  def scheduler(): ScheduledExecutorService =
    _scheduler

  private def schedulerPoolSize(config: Config): Int =
    config.getInt("kamon.scheduler-pool-size")

}
