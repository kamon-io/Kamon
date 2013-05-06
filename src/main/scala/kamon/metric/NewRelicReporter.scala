package kamon.metric

import com.newrelic.api.agent.NewRelic
import com.yammer.metrics.reporting.AbstractPollingReporter
import com.yammer.metrics.core._
import scala.collection.JavaConversions._


class NewRelicReporter(registry: MetricsRegistry, name: String) extends AbstractPollingReporter(registry, name) with MetricProcessor[String] {

  def processMeter(name: MetricName, meter: Metered, context: String) {
    println(s"Logging to NewRelic: ${meter.count()}")
    NewRelic.recordMetric("Custom/Actor/MessagesPerSecond", meter.meanRate().toFloat)



  }


  def processCounter(name: MetricName, counter: Counter, context: String) {}

  def processHistogram(name: MetricName, histogram: Histogram, context: String) {}

  def processTimer(name: MetricName, timer: Timer, context: String) {}

  def processGauge(name: MetricName, gauge: Gauge[_], context: String) {}


  def run() {
    for (entry <- getMetricsRegistry.groupedMetrics(MetricPredicate.ALL).entrySet) {
      for (subEntry <- entry.getValue.entrySet) {
        subEntry.getValue.processWith(this, subEntry.getKey, "")
      }
    }
  }
}
