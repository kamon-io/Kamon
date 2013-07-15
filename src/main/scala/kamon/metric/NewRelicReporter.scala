package kamon.metric

import com.codahale.metrics
import metrics._
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


/*  def processGauge(name: String, gauge: Gauge[_]) = {
    println(s"the value is: "+gauge.getValue)
    NewRelic.recordMetric("Custom/ActorSystem/activeCount", gauge.getValue.asInstanceOf[Float])
  }*/


  def report(gauges: util.SortedMap[String, Gauge[_]], counters: util.SortedMap[String, Counter], histograms: util.SortedMap[String, metrics.Histogram], meters: util.SortedMap[String, Meter], timers: util.SortedMap[String, Timer]) {
    //Process Meters
    meters.asScala.map{case(name, meter) => processMeter(name, meter)}

    //Process Meters
    counters.asScala.map{case(name, counter) => processCounter(name, counter)}

    // Gauges
    gauges.asScala.foreach{ case (name, gauge) => {
      val measure: Float = gauge.getValue.asInstanceOf[Number].floatValue()
      val fullMetricName = "Custom" + name
      NewRelic.recordMetric(fullMetricName, measure)
    }}
  }


}

object NewRelicReporter {
     def apply(registry: MetricRegistry) = new NewRelicReporter(registry, "NewRelic-reporter", metrics.MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS)
}