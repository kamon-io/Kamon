package kamon.metric

import com.codahale.metrics._
import java.util.concurrent.TimeUnit
import java.util
import com.newrelic.api.agent.NewRelic
import scala.collection.JavaConverters._


class NewRelicReporter(registry: MetricRegistry, name: String,filter: MetricFilter, rateUnit: TimeUnit, durationUnit: TimeUnit) extends ScheduledReporter(registry, name, filter, rateUnit, durationUnit) {

  def processMeter(name: String, meter: Meter) {
    println(s"Logging to NewRelic: ${meter.getCount()}")
    NewRelic.recordMetric("Custom/Actor/MessagesPerSecond", meter.getMeanRate().toFloat)
  }

  def report(gauges: util.SortedMap[String, Gauge[_]], counters: util.SortedMap[String, Counter], histograms: util.SortedMap[String, Histogram], meters: util.SortedMap[String, Meter], timers: util.SortedMap[String, Timer]) {
    //Process Meters
    meters.asScala.map{case(k,v) => processMeter(k,v)}
  }
}

object NewRelicReporter {
     def apply(registry: MetricRegistry) = new NewRelicReporter(registry, "NewRelic-reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS)
}