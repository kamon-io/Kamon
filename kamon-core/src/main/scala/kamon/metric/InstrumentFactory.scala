package kamon
package metric


import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.metric.InstrumentFactory.CustomInstrumentSettings
import kamon.util.MeasurementUnit

import scala.concurrent.duration._


private[kamon] class InstrumentFactory private (defaultHistogramDynamicRange: DynamicRange, defaultMMCounterDynamicRange: DynamicRange,
    defaultMMCounterSampleInterval: Duration, customSettings: Map[String, CustomInstrumentSettings]) {

  def buildHistogram(dynamicRange: Option[DynamicRange])(name: String, tags: Map[String, String], unit: MeasurementUnit): SnapshotableHistogram =
    new HdrHistogram(name, tags, unit, instrumentDynamicRange(name, dynamicRange.getOrElse(defaultHistogramDynamicRange)))

  def buildMinMaxCounter(dynamicRange: Option[DynamicRange], sampleInterval: Option[Duration])
      (name: String, tags: Map[String, String], unit: MeasurementUnit): SnapshotableMinMaxCounter =
    new PaddedMinMaxCounter(
      name,
      tags,
      buildHistogram(dynamicRange.orElse(Some(defaultMMCounterDynamicRange)))(name, tags, unit),
      instrumentSampleInterval(name, sampleInterval.getOrElse(defaultMMCounterSampleInterval)))

  def buildGauge(name: String, tags: Map[String, String], unit: MeasurementUnit): SnapshotableGauge =
    new AtomicLongGauge(name, tags, unit)

  def buildCounter(name: String, tags: Map[String, String], unit: MeasurementUnit): SnapshotableCounter =
    new LongAdderCounter(name, tags, unit)


  private def instrumentDynamicRange(instrumentName: String, dynamicRange: DynamicRange): DynamicRange =
    customSettings.get(instrumentName).fold(dynamicRange) { cs =>
      overrideDynamicRange(dynamicRange, cs)
    }

  private def instrumentSampleInterval(instrumentName: String, sampleInterval: Duration): Duration =
    customSettings.get(instrumentName).fold(sampleInterval) { cs =>
      cs.sampleInterval.getOrElse(sampleInterval)
    }

  private def overrideDynamicRange(defaultDynamicRange: DynamicRange, customSettings: CustomInstrumentSettings): DynamicRange =
    DynamicRange(
      customSettings.lowestDiscernibleValue.getOrElse(defaultDynamicRange.lowestDiscernibleValue),
      customSettings.highestTrackableValue.getOrElse(defaultDynamicRange.highestTrackableValue),
      customSettings.significantValueDigits.getOrElse(defaultDynamicRange.significantValueDigits)
    )
}

object InstrumentFactory {

  def fromConfig(config: Config): InstrumentFactory = {
    val factoryConfig = config.getConfig("kamon.metric.instrument-factory")
    val histogramDynamicRange = readDynamicRange(factoryConfig.getConfig("default-settings.histogram"))
    val mmCounterDynamicRange = readDynamicRange(factoryConfig.getConfig("default-settings.min-max-counter"))
    val mmCounterSampleInterval = factoryConfig.getDuration("default-settings.min-max-counter.sample-interval", TimeUnit.MILLISECONDS)

    val customSettings = factoryConfig.getConfig("custom-settings")
      .configurations
      .filter(nonEmptySection)
      .map(readCustomInstrumentSettings)

    new InstrumentFactory(histogramDynamicRange, mmCounterDynamicRange, mmCounterSampleInterval.millis, customSettings)
  }

  private def nonEmptySection(entry: (String, Config)): Boolean = entry match {
    case (_, config) => config.firstLevelKeys.nonEmpty
  }

  private def readCustomInstrumentSettings(entry: (String, Config)): (String, CustomInstrumentSettings) = {
    val (metricName, metricConfig) = entry
    val customSettings = CustomInstrumentSettings(
      if (metricConfig.hasPath("lowest-discernible-value")) Some(metricConfig.getLong("lowest-discernible-value")) else None,
      if (metricConfig.hasPath("highest-trackable-value")) Some(metricConfig.getLong("highest-trackable-value")) else None,
      if (metricConfig.hasPath("significant-value-digits")) Some(metricConfig.getInt("significant-value-digits")) else None,
      if (metricConfig.hasPath("sample-interval")) Some(metricConfig.getDuration("sample-interval", TimeUnit.MILLISECONDS).millis) else None
    )

    (metricName -> customSettings)
  }

  private def readDynamicRange(config: Config): DynamicRange =
    DynamicRange(
      lowestDiscernibleValue = config.getLong("lowest-discernible-value"),
      highestTrackableValue = config.getLong("highest-trackable-value"),
      significantValueDigits = config.getInt("significant-value-digits")
    )

  private case class CustomInstrumentSettings(
    lowestDiscernibleValue: Option[Long],
    highestTrackableValue: Option[Long],
    significantValueDigits: Option[Int],
    sampleInterval: Option[Duration]
  )
}