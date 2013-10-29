package kamon

import com.codahale.metrics.MetricRegistry

object Metrics {
  val registry = new MetricRegistry
}
