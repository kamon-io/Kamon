package kamon.jdbc.instrumentation

import kamon.jdbc.Metrics

object Mixin {

  trait HasConnectionPoolMetrics {
    def connectionPoolMetrics: Metrics.ConnectionPoolMetrics
    def setConnectionPoolMetrics(cpm: Metrics.ConnectionPoolMetrics): Unit
  }

  object HasConnectionPoolMetrics {
    def apply(): Mixin.HasConnectionPoolMetrics = new Mixin.HasConnectionPoolMetrics {
      @volatile private var tracker: Metrics.ConnectionPoolMetrics = _
      override def connectionPoolMetrics: Metrics.ConnectionPoolMetrics = this.tracker
      override def setConnectionPoolMetrics(cpm: Metrics.ConnectionPoolMetrics): Unit = this.tracker = cpm
    }
  }
}
