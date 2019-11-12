package kamon.newrelic.metrics

import com.newrelic.telemetry.metrics.{Gauge, Metric}
import kamon.metric.{Instrument, MetricSnapshot}
import kamon.newrelic.TagSetToAttributes.addTags
import kamon.newrelic.metrics.ConversionSupport.buildAttributes
import org.slf4j.LoggerFactory

object GaugeConverter {
  private val logger = LoggerFactory.getLogger(getClass)

  def convert(timestamp: Long, gauge: MetricSnapshot.Values[Double]): Seq[Metric] = {
    val attributes = buildAttributes(gauge)
    logger.debug("name: {} ; numberOfInstruments: {}", gauge.name, gauge.instruments.size)
    gauge.instruments.map { inst: Instrument.Snapshot[Double] =>
      new Gauge(gauge.name, inst.value, timestamp, addTags(Seq(inst.tags), attributes.copy().put("sourceMetricType", "gauge")))
    }
  }

}
