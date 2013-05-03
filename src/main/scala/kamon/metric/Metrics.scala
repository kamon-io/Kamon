package kamon.metric

import com.yammer.metrics.core.{MetricName, MetricsRegistry}
import scala.collection.mutable.{HashMap,SynchronizedMap}
import com.yammer.metrics.scala.{Meter, Counter, MetricsGroup, Timer}
import com.yammer.metrics.reporting.{ConsoleReporter, JmxReporter}
import scala.collection.mutable
import java.util.concurrent.TimeUnit

class Metrics {
  private lazy val metricsRegistry: MetricsRegistry = new MetricsRegistry()
  private lazy val metricsGroup = new MetricsGroup(this.getClass, metricsRegistry)

  private lazy val meters = new mutable.HashMap[String, Meter]
  private lazy val timers = new HashMap[String, Timer] with SynchronizedMap[String, Timer]
  private lazy val counters = new HashMap[String, Counter] with SynchronizedMap[String, Counter]



  val consoleReporter = ConsoleReporter.enable(metricsRegistry, 1, TimeUnit.SECONDS)
  val newrelicReport = new NewRelicReporter(metricsRegistry, "newrelic-reporter");
  newrelicReport.run()
  newrelicReport.start(1, TimeUnit.SECONDS)

  def incrementCounter(key: String) {
    counters.getOrElseUpdate(key, (metricsGroup.counter(s"${key}-counter"))).count
  }

  def markMeter(key: String) {
    meters.getOrElseUpdate(key, metricsGroup.meter(s"${key}-meter", "actor", "actor-message-counter", TimeUnit.SECONDS)).mark()
  }

  def trace[T](key: String)(f: => T): T = {
    val timer =  timers.getOrElseUpdate(key, (metricsGroup.timer(s"${key}-timer")) )
    timer.time(f)
  }

  def markAndCountMeter[T](key: String)(f: => T): T = {
    markMeter(key)
    f
  }

  def traceAndCount[T](key: String)(f: => T): T = {
    incrementCounter(key)
    trace(key) {
      f
    }
  }
}