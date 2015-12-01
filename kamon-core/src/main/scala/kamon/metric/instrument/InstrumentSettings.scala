package kamon.metric.instrument

import com.typesafe.config.Config
import kamon.metric.instrument.Histogram.DynamicRange

import scala.concurrent.duration.FiniteDuration

case class InstrumentCustomSettings(lowestDiscernibleValue: Option[Long], highestTrackableValue: Option[Long],
    precision: Option[Int], refreshInterval: Option[FiniteDuration]) {

  def combine(that: InstrumentSettings): InstrumentSettings =
    InstrumentSettings(
      DynamicRange(
        lowestDiscernibleValue.getOrElse(that.dynamicRange.lowestDiscernibleValue),
        highestTrackableValue.getOrElse(that.dynamicRange.highestTrackableValue),
        precision.getOrElse(that.dynamicRange.precision)),
      refreshInterval.orElse(that.refreshInterval))
}

object InstrumentCustomSettings {
  import kamon.util.ConfigTools.Syntax

  def fromConfig(config: Config): InstrumentCustomSettings =
    InstrumentCustomSettings(
      if (config.hasPath("lowest-discernible-value")) Some(config.getLong("lowest-discernible-value")) else None,
      if (config.hasPath("highest-trackable-value")) Some(config.getLong("highest-trackable-value")) else None,
      if (config.hasPath("precision")) Some(InstrumentSettings.parsePrecision(config.getString("precision"))) else None,
      if (config.hasPath("refresh-interval")) Some(config.getFiniteDuration("refresh-interval")) else None)

}

case class InstrumentSettings(dynamicRange: DynamicRange, refreshInterval: Option[FiniteDuration])

object InstrumentSettings {

  def readDynamicRange(config: Config): DynamicRange =
    DynamicRange(
      config.getLong("lowest-discernible-value"),
      config.getLong("highest-trackable-value"),
      parsePrecision(config.getString("precision")))

  def parsePrecision(stringValue: String): Int = stringValue match {
    case "low"    ⇒ 1
    case "normal" ⇒ 2
    case "fine"   ⇒ 3
    case other    ⇒ sys.error(s"Invalid precision configuration [$other] found, valid options are: [low|normal|fine].")
  }
}

case class DefaultInstrumentSettings(histogram: InstrumentSettings, minMaxCounter: InstrumentSettings, gauge: InstrumentSettings)

object DefaultInstrumentSettings {

  def fromConfig(config: Config): DefaultInstrumentSettings = {
    import kamon.util.ConfigTools.Syntax

    val histogramSettings = InstrumentSettings(InstrumentSettings.readDynamicRange(config.getConfig("histogram")), None)
    val minMaxCounterSettings = InstrumentSettings(InstrumentSettings.readDynamicRange(config.getConfig("min-max-counter")),
      Some(config.getFiniteDuration("min-max-counter.refresh-interval")))
    val gaugeSettings = InstrumentSettings(InstrumentSettings.readDynamicRange(config.getConfig("gauge")),
      Some(config.getFiniteDuration("gauge.refresh-interval")))

    DefaultInstrumentSettings(histogramSettings, minMaxCounterSettings, gaugeSettings)
  }
}