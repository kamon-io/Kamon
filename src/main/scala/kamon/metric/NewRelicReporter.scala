package kamon.metric

import com.codahale.metrics._
import java.util.concurrent.TimeUnit
import java.util
import com.newrelic.api.agent.NewRelic
import scala.collection.JavaConverters._


class NewRelicReporter(registry: MetricRegistry, name: String,filter: MetricFilter, rateUnit: TimeUnit, durationUnit: TimeUnit) extends ScheduledReporter(registry, name, filter, rateUnit, durationUnit) {

  private[NewRelicReporter] def processMeter(name: String, meter: Meter) {
    NewRelic.recordMetric("Custom/Actor/MessagesPerSecond", meter.getMeanRate().toFloat)
  }

  private[NewRelicReporter] def processCounter(name:String, counter:Counter) {
    println(s"Logging to NewRelic: ${counter.getCount}")

  }
  def report(gauges: util.SortedMap[String, Gauge[_]], counters: util.SortedMap[String, Counter], histograms: util.SortedMap[String, Histogram], meters: util.SortedMap[String, Meter], timers: util.SortedMap[String, Timer]) {
    //Process Meters
    meters.asScala.map{case(name, meter) => processMeter(name, meter)}

    //Process Meters
    counters.asScala.map{case(name, counter) => processCounter(name, counter)}
  }
}

object NewRelicReporter {
     def apply(registry: MetricRegistry) = new NewRelicReporter(registry, "NewRelic-reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS)
}