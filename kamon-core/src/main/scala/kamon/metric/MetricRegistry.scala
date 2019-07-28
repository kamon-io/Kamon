/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon
package metric

import java.time.{Duration, Instant}
import java.util.concurrent.ScheduledExecutorService

import com.typesafe.config.Config
import kamon.metric.Metric.BaseMetric
import kamon.status.Status
import kamon.util.Clock
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap

/**
  * Handles creation and snapshotting of metrics. If a metric is created twice, the very same instance will be returned.
  * If an attempt to create an existent metric with different settings is made, the new settings are ignored in favor of
  * those of the already registered metric.
  *
  */
class MetricRegistry(config: Config, scheduler: ScheduledExecutorService, clock: Clock) {
  private val _logger = LoggerFactory.getLogger(classOf[MetricRegistry])
  private val _metrics = TrieMap.empty[String, BaseMetric[_, _, _]]
  @volatile private var _lastSnapshotInstant: Instant = clock.instant()
  @volatile private var _factory: MetricFactory = MetricFactory.from(config, scheduler, clock)


  /**
    * Retrieves or registers a new counter-based metric.
    */
  def counter(name: String, description: Option[String], unit: Option[MeasurementUnit], autoUpdateInterval: Option[Duration]):
      Metric.Counter = {

    val metric = _metrics.atomicGetOrElseUpdate(name, _factory.counter(name, description, unit, autoUpdateInterval))
        .asInstanceOf[Metric.Counter]

    checkInstrumentType(name, Instrument.Type.Counter, metric)
    checkDescription(metric.name, metric.description, description)
    checkUnit(metric.name, metric.settings.unit, unit)
    checkAutoUpdate(metric.name, metric.settings.autoUpdateInterval, autoUpdateInterval)
    metric
  }

  /**
    * Retrieves or registers a new gauge-based metric.
    */
  def gauge(name: String, description: Option[String], unit: Option[MeasurementUnit], autoUpdateInterval: Option[Duration]):
  Metric.Gauge = {

    val metric = _metrics.atomicGetOrElseUpdate(name, _factory.gauge(name, description, unit, autoUpdateInterval))
      .asInstanceOf[Metric.Gauge]

    checkInstrumentType(name, Instrument.Type.Gauge, metric)
    checkDescription(metric.name, metric.description, description)
    checkUnit(metric.name, metric.settings.unit, unit)
    checkAutoUpdate(metric.name, metric.settings.autoUpdateInterval, autoUpdateInterval)
    metric
  }

  /**
    * Retrieves or registers a new histogram-based metric.
    */
  def histogram(name: String, description: Option[String], unit: Option[MeasurementUnit], dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]): Metric.Histogram = {

    val metric = _metrics.atomicGetOrElseUpdate(name, _factory.histogram(name, description, unit, dynamicRange,
      autoUpdateInterval)).asInstanceOf[Metric.Histogram]

    checkInstrumentType(name, Instrument.Type.Histogram, metric)
    checkDescription(metric.name, metric.description, description)
    checkUnit(metric.name, metric.settings.unit, unit)
    checkDynamicRange(metric.name, metric.settings.dynamicRange, dynamicRange)
    checkAutoUpdate(metric.name, metric.settings.autoUpdateInterval, autoUpdateInterval)
    metric
  }

  /**
    * Retrieves or registers a new timer-based metric.
    */
  def timer(name: String, description: Option[String], dynamicRange: Option[DynamicRange], autoUpdateInterval: Option[Duration]): Metric.Timer = {

    val metric = _metrics.atomicGetOrElseUpdate(name, _factory.timer(name, description, Some(MeasurementUnit.time.nanoseconds),
      dynamicRange, autoUpdateInterval)).asInstanceOf[Metric.Timer]

    checkInstrumentType(name, Instrument.Type.Timer, metric)
    checkDescription(metric.name, metric.description, description)
    checkDynamicRange(metric.name, metric.settings.dynamicRange, dynamicRange)
    checkAutoUpdate(metric.name, metric.settings.autoUpdateInterval, autoUpdateInterval)
    metric
  }

  /**
    * Retrieves or registers a new range sampler-based metric.
    */
  def rangeSampler(name: String, description: Option[String], unit: Option[MeasurementUnit], dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]): Metric.RangeSampler = {

    val metric = _metrics.atomicGetOrElseUpdate(name, _factory.rangeSampler(name, description, unit, dynamicRange,
      autoUpdateInterval)).asInstanceOf[Metric.RangeSampler]

    checkInstrumentType(name, Instrument.Type.RangeSampler, metric)
    checkDescription(metric.name, metric.description, description)
    checkUnit(metric.name, metric.settings.unit, unit)
    checkDynamicRange(metric.name, metric.settings.dynamicRange, dynamicRange)
    checkAutoUpdate(metric.name, metric.settings.autoUpdateInterval, autoUpdateInterval)
    metric
  }

  /**
    * Reconfigures the registry using the provided configuration.
    */
  def reconfigure(newConfig: Config): Unit = {
    _factory = MetricFactory.from(newConfig, scheduler, clock)
  }

  private def checkInstrumentType(name: String, instrumentType: Instrument.Type, metric: Metric[_, _]): Unit =
    if(!instrumentType.implementation.isInstance(metric))
      sys.error(s"Cannot redefine metric [$name] as a [${instrumentType.name}], it was already registered as a [${metric.getClass.getName}]")

  private def checkDescription(name: String, description: String, providedDescription: Option[String]): Unit =
    if(providedDescription.exists(d => d != description))
      _logger.warn(s"Ignoring new description [${providedDescription.getOrElse("")}] for metric [${name}]")

  private def checkUnit(name: String, unit: MeasurementUnit, providedUnit: Option[MeasurementUnit]): Unit =
    if(providedUnit.exists(u => u != unit))
      _logger.warn(s"Ignoring new unit [${providedUnit.getOrElse("")}] for metric [${name}]")

  private def checkAutoUpdate(name: String, autoUpdateInterval: Duration, providedAutoUpdateInterval: Option[Duration]): Unit =
    if(providedAutoUpdateInterval.exists(u => u != autoUpdateInterval))
      _logger.warn(s"Ignoring new auto-update interval [${providedAutoUpdateInterval.getOrElse("")}] for metric [${name}]")

  private def checkDynamicRange(name: String, dynamicRange: DynamicRange, providedDynamicRange: Option[DynamicRange]): Unit =
    if(providedDynamicRange.exists(dr => dr != dynamicRange))
      _logger.warn(s"Ignoring new dynamic range [${providedDynamicRange.getOrElse("")}] for metric [${name}]")



  /**
    * Creates a period snapshot of all metrics contained in this registry. The period always starts at the instant of
    * the last snapshot taken in which the state was reset and until the current instant. The special case of the first
    * snapshot uses the registry creation instant as the starting point.
    */
  def snapshot(resetState: Boolean): PeriodSnapshot = synchronized {
    var counters = List.empty[MetricSnapshot.Values[Long]]
    var gauges = List.empty[MetricSnapshot.Values[Double]]
    var histograms = List.empty[MetricSnapshot.Distributions]
    var timers = List.empty[MetricSnapshot.Distributions]
    var rangeSamplers = List.empty[MetricSnapshot.Distributions]

    _metrics.foreach {
      case (_, metric) => metric match {
        case m: Metric.Counter      => counters = m.snapshot(resetState).asInstanceOf[MetricSnapshot.Values[Long]] :: counters
        case m: Metric.Gauge        => gauges = m.snapshot(resetState).asInstanceOf[MetricSnapshot.Values[Double]] :: gauges
        case m: Metric.Histogram    => histograms = m.snapshot(resetState).asInstanceOf[MetricSnapshot.Distributions] :: histograms
        case m: Metric.Timer        => timers = m.snapshot(resetState).asInstanceOf[MetricSnapshot.Distributions] :: timers
        case m: Metric.RangeSampler => rangeSamplers = m.snapshot(resetState).asInstanceOf[MetricSnapshot.Distributions] :: rangeSamplers
      }
    }

    val periodStart = _lastSnapshotInstant
    val periodEnd = clock.instant()
    _lastSnapshotInstant = periodEnd

    PeriodSnapshot(periodStart, periodEnd, counters, gauges, histograms, timers, rangeSamplers)
  }

  /** Returns the current status of all metrics contained in the registry */
  def status(): Status.MetricRegistry =
    Status.MetricRegistry(_metrics.values.map(_.status()).toSeq)
}
