package kamon

import java.time.Duration

import kamon.metric._


trait Metrics extends MetricLookup { self: Configuration with Utilities =>
  private val _metricsRegistry = new MetricRegistry(self.config(), self.scheduler())

  override def histogram(name: String, unit: MeasurementUnit, dynamicRange: Option[DynamicRange]): HistogramMetric =
    _metricsRegistry.histogram(name, unit, dynamicRange)

  override def counter(name: String, unit: MeasurementUnit): CounterMetric =
    _metricsRegistry.counter(name, unit)

  override def gauge(name: String, unit: MeasurementUnit): GaugeMetric =
    _metricsRegistry.gauge(name, unit)

  override def rangeSampler(name: String, unit: MeasurementUnit, sampleInterval: Option[Duration],
    dynamicRange: Option[DynamicRange]): RangeSamplerMetric =
    _metricsRegistry.rangeSampler(name, unit, dynamicRange, sampleInterval)

  override def timer(name: String, dynamicRange: Option[DynamicRange]): TimerMetric =
    _metricsRegistry.timer(name, dynamicRange)


  protected def metricRegistry(): MetricRegistry =
    _metricsRegistry

}
