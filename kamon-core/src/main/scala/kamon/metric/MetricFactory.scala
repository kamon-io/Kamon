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
package metric

import java.util.concurrent.ScheduledExecutorService

import com.typesafe.config.Config
import java.time.Duration

import kamon.metric.Metric.BaseMetric
import kamon.tag.TagSet
import kamon.util.Clock


/**
  * Creates new metric instances taking default and custom settings into account. This class only handles the creation
  * and configuration of metrics and does not do any effort on checking whether a metric has already been created or
  * not; that kind of verification is handled on the Kamon metric registry.
  *
  */
class MetricFactory private (defaultCounterSettings: Metric.Settings.ValueInstrument, defaultGaugeSettings: Metric.Settings.ValueInstrument,
    defaultHistogramSettings: Metric.Settings.DistributionInstrument, defaultTimerSettings: Metric.Settings.DistributionInstrument,
    defaultRangeSamplerSettings: Metric.Settings.DistributionInstrument,  customSettings: Map[String, MetricFactory.CustomSettings],
    scheduler: ScheduledExecutorService, clock: Clock) {


  /**
    * Creates a new counter-based metric, backed by the Counter.LongAdder implementation.
    */
  def counter(name: String, description: Option[String], unit: Option[MeasurementUnit], autoUpdateInterval: Option[Duration]):
    BaseMetric[Counter, Metric.Settings.ValueInstrument, Long] with Metric.Counter = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ValueInstrument (
      unit.getOrElse(defaultCounterSettings.unit),
      autoUpdateInterval.getOrElse(defaultCounterSettings.autoUpdateInterval)
    )

    val builder = (metric: BaseMetric[Counter, Metric.Settings.ValueInstrument, Long], tags: TagSet) =>
      new Counter.LongAdder(metric, tags)

    new BaseMetric[Counter, Metric.Settings.ValueInstrument, Long](name, metricDescription, metricSettings,
      builder, scheduler) with Metric.Counter {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Counter

      override protected def buildMetricSnapshot(
        metric: Metric[Counter, Metric.Settings.ValueInstrument],
        instruments: Map[TagSet, Long]
      ): MetricSnapshot.Value[Long] = MetricSnapshot.Value[Long](metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new counter-based metric, backed by the Counter.LongAdder implementation.
    */
  def gauge(name: String, description: Option[String], unit: Option[MeasurementUnit], autoUpdateInterval: Option[Duration]):
    BaseMetric[Gauge, Metric.Settings.ValueInstrument, Double] with Metric.Gauge = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ValueInstrument (
      unit.getOrElse(defaultGaugeSettings.unit),
      autoUpdateInterval.getOrElse(defaultGaugeSettings.autoUpdateInterval)
    )

    val builder = (metric: BaseMetric[Gauge, Metric.Settings.ValueInstrument, Double], tags: TagSet) =>
      new Gauge.Volatile(metric, tags)

    new BaseMetric[Gauge, Metric.Settings.ValueInstrument, Double](name, metricDescription, metricSettings,
      builder, scheduler) with Metric.Gauge {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Gauge

      override protected def buildMetricSnapshot(
        metric: Metric[Gauge, Metric.Settings.ValueInstrument],
        instruments: Map[TagSet, Double]
      ): MetricSnapshot.Value[Double] = MetricSnapshot.Value[Double](metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def histogram(name: String, description: Option[String], unit: Option[MeasurementUnit], dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]): BaseMetric[Histogram, Metric.Settings.DistributionInstrument, Distribution] with Metric.Histogram = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.DistributionInstrument (
      unit.getOrElse(defaultHistogramSettings.unit),
      autoUpdateInterval.getOrElse(defaultHistogramSettings.autoUpdateInterval),
      dynamicRange.getOrElse(defaultHistogramSettings.dynamicRange)
    )

    val builder = (metric: BaseMetric[Histogram, Metric.Settings.DistributionInstrument, Distribution], tags: TagSet) =>
      new Histogram.Atomic(metric, tags, metricSettings.dynamicRange)

    new BaseMetric[Histogram, Metric.Settings.DistributionInstrument, Distribution](name, metricDescription, metricSettings,
      builder, scheduler) with Metric.Histogram {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Histogram

      override protected def buildMetricSnapshot(
        metric: Metric[Histogram, Metric.Settings.DistributionInstrument],
        instruments: Map[TagSet,Distribution]
      ): MetricSnapshot.Distribution = MetricSnapshot.Distribution(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def timer(name: String, description: Option[String], unit: Option[MeasurementUnit], dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]): BaseMetric[Timer, Metric.Settings.DistributionInstrument, Distribution] with Metric.Timer = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.DistributionInstrument (
      unit.getOrElse(defaultTimerSettings.unit),
      autoUpdateInterval.getOrElse(defaultTimerSettings.autoUpdateInterval),
      dynamicRange.getOrElse(defaultTimerSettings.dynamicRange)
    )

    val builder = (metric: BaseMetric[Timer, Metric.Settings.DistributionInstrument, Distribution], tags: TagSet) =>
      new Timer.Atomic(metric, tags, metricSettings.dynamicRange, clock)

    new BaseMetric[Timer, Metric.Settings.DistributionInstrument, Distribution](name, metricDescription, metricSettings,
      builder, scheduler) with Metric.Timer {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Timer

      override protected def buildMetricSnapshot(
        metric: Metric[Timer, Metric.Settings.DistributionInstrument],
        instruments: Map[TagSet,Distribution]
      ): MetricSnapshot.Distribution = MetricSnapshot.Distribution(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def rangeSampler(name: String, description: Option[String], unit: Option[MeasurementUnit], dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]): BaseMetric[RangeSampler, Metric.Settings.DistributionInstrument, Distribution] with Metric.RangeSampler = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.DistributionInstrument (
      unit.getOrElse(defaultRangeSamplerSettings.unit),
      autoUpdateInterval.getOrElse(defaultRangeSamplerSettings.autoUpdateInterval),
      dynamicRange.getOrElse(defaultRangeSamplerSettings.dynamicRange)
    )

    val builder = (metric: BaseMetric[RangeSampler, Metric.Settings.DistributionInstrument, Distribution], tags: TagSet) =>
      new RangeSampler.Atomic(metric, tags, metricSettings.dynamicRange)

    new BaseMetric[RangeSampler, Metric.Settings.DistributionInstrument, Distribution](name, metricDescription, metricSettings,
      builder, scheduler) with Metric.RangeSampler {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.RangeSampler

      override protected def buildMetricSnapshot(
        metric: Metric[RangeSampler, Metric.Settings.DistributionInstrument],
        instruments: Map[TagSet,Distribution]
      ): MetricSnapshot.Distribution = MetricSnapshot.Distribution(metric.name, metric.description, metric.settings, instruments)
    }
  }
}

object MetricFactory {

  def from(config: Config, scheduler: ScheduledExecutorService, clock: Clock): MetricFactory = {
    val factoryConfig = config.getConfig("kamon.metric.factory")
    val defaultCounterSettings = Metric.Settings.ValueInstrument (
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.counter.auto-update-interval")
    )

    val defaultGaugeSettings = Metric.Settings.ValueInstrument (
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.gauge.auto-update-interval")
    )

    val defaultHistogramSettings = Metric.Settings.DistributionInstrument (
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.histogram.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.histogram"))
    )

    val defaultTimerSettings = Metric.Settings.DistributionInstrument (
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.timer.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.timer"))
    )

    val defaultRangeSamplerSettings = Metric.Settings.DistributionInstrument (
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.range-sampler.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.range-sampler"))
    )

    val customSettings = factoryConfig.getConfig("custom-settings")
      .configurations
      .filter(nonEmptySection)
      .map(readCustomSettings)

    new MetricFactory(defaultCounterSettings, defaultGaugeSettings, defaultHistogramSettings, defaultTimerSettings,
      defaultRangeSamplerSettings, customSettings, scheduler, clock)
  }

  private def nonEmptySection(entry: (String, Config)): Boolean = entry match {
    case (_, config) => config.topLevelKeys.nonEmpty
  }

  private def readCustomSettings(entry: (String, Config)): (String, CustomSettings) = {
    val (metricName, metricConfig) = entry
    val customSettings = CustomSettings(
      if (metricConfig.hasPath("auto-update-interval")) Some(metricConfig.getDuration("auto-update-interval")) else None,
      if (metricConfig.hasPath("lowest-discernible-value")) Some(metricConfig.getLong("lowest-discernible-value")) else None,
      if (metricConfig.hasPath("highest-trackable-value")) Some(metricConfig.getLong("highest-trackable-value")) else None,
      if (metricConfig.hasPath("significant-value-digits")) Some(metricConfig.getInt("significant-value-digits")) else None,
      if (metricConfig.hasPath("sample-interval")) Some(metricConfig.getDuration("sample-interval")) else None
    )
    metricName -> customSettings
  }

  private def readDynamicRange(config: Config): DynamicRange =
    DynamicRange(
      lowestDiscernibleValue = config.getLong("lowest-discernible-value"),
      highestTrackableValue = config.getLong("highest-trackable-value"),
      significantValueDigits = config.getInt("significant-value-digits")
    )

  private case class CustomSettings (
    autoUpdateInterval: Option[Duration],
    lowestDiscernibleValue: Option[Long],
    highestTrackableValue: Option[Long],
    significantValueDigits: Option[Int],
    sampleInterval: Option[Duration]
  )
}