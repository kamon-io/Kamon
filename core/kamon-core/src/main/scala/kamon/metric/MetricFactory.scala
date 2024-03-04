/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package metric

import com.typesafe.config.Config

import java.time.Duration
import kamon.metric.Metric.{BaseMetric, Settings}
import kamon.tag.TagSet
import kamon.util.Clock

import java.util.concurrent.ScheduledExecutorService

/**
  * Creates new metric instances taking default and custom settings into account. This class only handles the creation
  * and configuration of metrics and does not do any effort on checking whether a metric has already been created or
  * not; that kind of verification is handled on the Kamon metric registry.
  *
  */
class MetricFactory private (
  defaultCounterSettings: Metric.Settings.ForValueInstrument,
  defaultGaugeSettings: Metric.Settings.ForValueInstrument,
  defaultHistogramSettings: Metric.Settings.ForDistributionInstrument,
  defaultTimerSettings: Metric.Settings.ForDistributionInstrument,
  defaultRangeSamplerSettings: Metric.Settings.ForDistributionInstrument,
  customSettings: Map[String, MetricFactory.CustomSettings],
  clock: Clock,
  scheduler: Option[ScheduledExecutorService]
) {

  /**
    * Creates a new counter-based metric, backed by the Counter.LongAdder implementation.
    */
  def counter(
    name: String,
    description: Option[String],
    unit: Option[MeasurementUnit],
    autoUpdateInterval: Option[Duration]
  ): BaseMetric[Counter, Metric.Settings.ForValueInstrument, Long] with Metric.Counter = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ForValueInstrument(
      unit.getOrElse(defaultCounterSettings.unit),
      resolveAutoUpdateInterval(name, autoUpdateInterval, defaultCounterSettings.autoUpdateInterval)
    )

    val builder = (metric: BaseMetric[Counter, Metric.Settings.ForValueInstrument, Long], tags: TagSet) =>
      new Counter.LongAdder(metric, tags)

    new BaseMetric[Counter, Metric.Settings.ForValueInstrument, Long](
      name,
      metricDescription,
      metricSettings,
      builder,
      scheduler
    ) with Metric.Counter {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Counter

      override protected def buildMetricSnapshot(
        metric: Metric[Counter, Settings.ForValueInstrument],
        instruments: Seq[Instrument.Snapshot[Long]]
      ): MetricSnapshot.Values[Long] =
        MetricSnapshot.ofValues(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new counter-based metric, backed by the Counter.LongAdder implementation.
    */
  def gauge(
    name: String,
    description: Option[String],
    unit: Option[MeasurementUnit],
    autoUpdateInterval: Option[Duration]
  ): BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double] with Metric.Gauge = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ForValueInstrument(
      unit.getOrElse(defaultGaugeSettings.unit),
      resolveAutoUpdateInterval(name, autoUpdateInterval, defaultGaugeSettings.autoUpdateInterval)
    )

    val builder = (metric: BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double], tags: TagSet) =>
      new Gauge.Volatile(metric, tags)

    new BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double](
      name,
      metricDescription,
      metricSettings,
      builder,
      scheduler
    ) with Metric.Gauge {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Gauge

      override protected def buildMetricSnapshot(
        metric: Metric[Gauge, Settings.ForValueInstrument],
        instruments: Seq[Instrument.Snapshot[Double]]
      ): MetricSnapshot.Values[Double] =
        MetricSnapshot.ofValues(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def histogram(
    name: String,
    description: Option[String],
    unit: Option[MeasurementUnit],
    dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]
  ): BaseMetric[Histogram, Metric.Settings.ForDistributionInstrument, Distribution] with Metric.Histogram = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ForDistributionInstrument(
      unit.getOrElse(defaultHistogramSettings.unit),
      resolveAutoUpdateInterval(name, autoUpdateInterval, defaultHistogramSettings.autoUpdateInterval),
      resolveDynamicRange(name, dynamicRange, defaultHistogramSettings.dynamicRange)
    )

    val builder =
      (metric: BaseMetric[Histogram, Metric.Settings.ForDistributionInstrument, Distribution], tags: TagSet) =>
        new Histogram.Atomic(metric, tags, metricSettings.dynamicRange)

    new BaseMetric[Histogram, Metric.Settings.ForDistributionInstrument, Distribution](
      name,
      metricDescription,
      metricSettings,
      builder,
      scheduler
    ) with Metric.Histogram {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Histogram

      override protected def buildMetricSnapshot(
        metric: Metric[Histogram, Settings.ForDistributionInstrument],
        instruments: Seq[Instrument.Snapshot[Distribution]]
      ): MetricSnapshot.Distributions =
        MetricSnapshot.ofDistributions(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def timer(
    name: String,
    description: Option[String],
    unit: Option[MeasurementUnit],
    dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]
  ): BaseMetric[Timer, Metric.Settings.ForDistributionInstrument, Distribution] with Metric.Timer = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ForDistributionInstrument(
      unit.getOrElse(defaultTimerSettings.unit),
      resolveAutoUpdateInterval(name, autoUpdateInterval, defaultTimerSettings.autoUpdateInterval),
      resolveDynamicRange(name, dynamicRange, defaultTimerSettings.dynamicRange)
    )

    val builder = (metric: BaseMetric[Timer, Metric.Settings.ForDistributionInstrument, Distribution], tags: TagSet) =>
      new Timer.Atomic(metric, tags, metricSettings.dynamicRange, clock)

    new BaseMetric[Timer, Metric.Settings.ForDistributionInstrument, Distribution](
      name,
      metricDescription,
      metricSettings,
      builder,
      scheduler
    ) with Metric.Timer {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.Timer

      override protected def buildMetricSnapshot(
        metric: Metric[Timer, Settings.ForDistributionInstrument],
        instruments: Seq[Instrument.Snapshot[Distribution]]
      ): MetricSnapshot.Distributions =
        MetricSnapshot.ofDistributions(metric.name, metric.description, metric.settings, instruments)
    }
  }

  /**
    * Creates a new histogram-based metric, backed by the Histogram.Atomic implementation.
    */
  def rangeSampler(
    name: String,
    description: Option[String],
    unit: Option[MeasurementUnit],
    dynamicRange: Option[DynamicRange],
    autoUpdateInterval: Option[Duration]
  ): BaseMetric[RangeSampler, Metric.Settings.ForDistributionInstrument, Distribution] with Metric.RangeSampler = {

    val metricDescription = description.getOrElse("")
    val metricSettings = Metric.Settings.ForDistributionInstrument(
      unit.getOrElse(defaultRangeSamplerSettings.unit),
      resolveAutoUpdateInterval(name, autoUpdateInterval, defaultRangeSamplerSettings.autoUpdateInterval),
      resolveDynamicRange(name, dynamicRange, defaultRangeSamplerSettings.dynamicRange)
    )

    val builder =
      (metric: BaseMetric[RangeSampler, Metric.Settings.ForDistributionInstrument, Distribution], tags: TagSet) =>
        new RangeSampler.Atomic(metric, tags, metricSettings.dynamicRange)

    new BaseMetric[RangeSampler, Metric.Settings.ForDistributionInstrument, Distribution](
      name,
      metricDescription,
      metricSettings,
      builder,
      scheduler
    ) with Metric.RangeSampler {

      override protected def instrumentType: Instrument.Type =
        Instrument.Type.RangeSampler

      override protected def buildMetricSnapshot(
        metric: Metric[RangeSampler, Settings.ForDistributionInstrument],
        instruments: Seq[Instrument.Snapshot[Distribution]]
      ): MetricSnapshot.Distributions =
        MetricSnapshot.ofDistributions(metric.name, metric.description, metric.settings, instruments)
    }
  }

  private def resolveAutoUpdateInterval(
    metricName: String,
    codeOption: Option[Duration],
    defaultValue: Duration
  ): Duration = {
    customSettings.get(metricName)
      .flatMap(_.autoUpdateInterval)
      .getOrElse(codeOption.getOrElse(defaultValue))
  }

  private def resolveDynamicRange(
    metricName: String,
    codeDynamicRange: Option[DynamicRange],
    default: DynamicRange
  ): DynamicRange = {
    val overrides = customSettings.get(metricName)
    val base = codeDynamicRange.getOrElse(default)

    DynamicRange(
      lowestDiscernibleValue = overrides.flatMap(_.lowestDiscernibleValue).getOrElse(base.lowestDiscernibleValue),
      highestTrackableValue = overrides.flatMap(_.highestTrackableValue).getOrElse(base.highestTrackableValue),
      significantValueDigits = overrides.flatMap(_.significantValueDigits).getOrElse(base.significantValueDigits)
    )
  }
}

object MetricFactory {

  def from(config: Config, clock: Clock, scheduler: Option[ScheduledExecutorService]): MetricFactory = {
    val factoryConfig = config.getConfig("kamon.metric.factory")
    val defaultCounterSettings = Metric.Settings.ForValueInstrument(
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.counter.auto-update-interval")
    )

    val defaultGaugeSettings = Metric.Settings.ForValueInstrument(
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.gauge.auto-update-interval")
    )

    val defaultHistogramSettings = Metric.Settings.ForDistributionInstrument(
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.histogram.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.histogram"))
    )

    val defaultTimerSettings = Metric.Settings.ForDistributionInstrument(
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.timer.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.timer"))
    )

    val defaultRangeSamplerSettings = Metric.Settings.ForDistributionInstrument(
      MeasurementUnit.none,
      factoryConfig.getDuration("default-settings.range-sampler.auto-update-interval"),
      readDynamicRange(factoryConfig.getConfig("default-settings.range-sampler"))
    )

    val customSettings = factoryConfig.getConfig("custom-settings")
      .configurations
      .filter(nonEmptySection)
      .map(readCustomSettings)

    new MetricFactory(
      defaultCounterSettings,
      defaultGaugeSettings,
      defaultHistogramSettings,
      defaultTimerSettings,
      defaultRangeSamplerSettings,
      customSettings,
      clock,
      scheduler
    )
  }

  private def nonEmptySection(entry: (String, Config)): Boolean = entry match {
    case (_, config) => config.topLevelKeys.nonEmpty
  }

  private def readCustomSettings(entry: (String, Config)): (String, CustomSettings) = {
    val (metricName, metricConfig) = entry
    val customSettings = CustomSettings(
      if (metricConfig.hasPath("auto-update-interval")) Some(metricConfig.getDuration("auto-update-interval"))
      else None,
      if (metricConfig.hasPath("lowest-discernible-value")) Some(metricConfig.getLong("lowest-discernible-value"))
      else None,
      if (metricConfig.hasPath("highest-trackable-value")) Some(metricConfig.getLong("highest-trackable-value"))
      else None,
      if (metricConfig.hasPath("significant-value-digits")) Some(metricConfig.getInt("significant-value-digits"))
      else None
    )
    metricName -> customSettings
  }

  private def readDynamicRange(config: Config): DynamicRange =
    DynamicRange(
      lowestDiscernibleValue = config.getLong("lowest-discernible-value"),
      highestTrackableValue = config.getLong("highest-trackable-value"),
      significantValueDigits = config.getInt("significant-value-digits")
    )

  private case class CustomSettings(
    autoUpdateInterval: Option[Duration],
    lowestDiscernibleValue: Option[Long],
    highestTrackableValue: Option[Long],
    significantValueDigits: Option[Int]
  )
}
