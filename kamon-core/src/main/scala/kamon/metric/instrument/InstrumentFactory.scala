package kamon.metric.instrument

import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.Histogram.DynamicRange

import scala.concurrent.duration.FiniteDuration

case class InstrumentFactory(configurations: Map[String, InstrumentCustomSettings], defaults: DefaultInstrumentSettings, scheduler: RefreshScheduler) {

  private def resolveSettings(instrumentName: String, codeSettings: Option[InstrumentSettings], default: InstrumentSettings): InstrumentSettings = {
    configurations.get(instrumentName).flatMap { customSettings ⇒
      codeSettings.map(cs ⇒ customSettings.combine(cs)) orElse (Some(customSettings.combine(default)))

    } getOrElse (codeSettings.getOrElse(default))
  }

  def createHistogram(name: String, dynamicRange: Option[DynamicRange] = None): Histogram = {
    val settings = resolveSettings(name, dynamicRange.map(dr ⇒ InstrumentSettings(dr, None)), defaults.histogram)
    Histogram(settings.dynamicRange)
  }

  def createMinMaxCounter(name: String, dynamicRange: Option[DynamicRange] = None, refreshInterval: Option[FiniteDuration] = None): MinMaxCounter = {
    val settings = resolveSettings(name, dynamicRange.map(dr ⇒ InstrumentSettings(dr, refreshInterval)), defaults.minMaxCounter)
    MinMaxCounter(settings.dynamicRange, settings.refreshInterval.get, scheduler)
  }

  def createGauge(name: String, dynamicRange: Option[DynamicRange] = None, refreshInterval: Option[FiniteDuration] = None,
    valueCollector: CurrentValueCollector): Gauge = {

    val settings = resolveSettings(name, dynamicRange.map(dr ⇒ InstrumentSettings(dr, refreshInterval)), defaults.gauge)
    Gauge(settings.dynamicRange, settings.refreshInterval.get, scheduler, valueCollector)
  }

  def createCounter(): Counter = Counter()
}