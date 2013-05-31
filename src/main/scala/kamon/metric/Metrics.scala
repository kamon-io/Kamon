package kamon.metric

import java.util.concurrent.TimeUnit
import com.codahale.metrics._

object Metrics {
  val metricsRegistry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(metricsRegistry)
  val newrelicReporter = NewRelicReporter(metricsRegistry)

  newrelicReporter.start(5, TimeUnit.SECONDS)
  consoleReporter.build().start(5, TimeUnit.SECONDS)
}