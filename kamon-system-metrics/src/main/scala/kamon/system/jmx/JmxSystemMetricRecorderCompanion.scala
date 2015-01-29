package kamon.system.jmx

import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{ Entity, EntityRecorder, MetricsExtension }

abstract class JmxSystemMetricRecorderCompanion(metricName: String) {
  def register(metricsExtension: MetricsExtension): EntityRecorder = {
    val instrumentFactory = metricsExtension.instrumentFactory("system-metric")
    metricsExtension.register(Entity(metricName, "system-metric"), apply(instrumentFactory)).recorder
  }

  def apply(instrumentFactory: InstrumentFactory): EntityRecorder
}