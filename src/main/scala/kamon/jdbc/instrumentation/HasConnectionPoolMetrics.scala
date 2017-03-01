package kamon.jdbc.instrumentation

import kamon.jdbc.metric.ConnectionPoolMetrics

object HasConnectionPoolMetrics {
  trait Mixin {
    def connectionPoolMetrics: ConnectionPoolMetrics
    def setConnectionPoolMetrics(cpm: ConnectionPoolMetrics): Unit
  }


  def apply(): HasConnectionPoolMetrics.Mixin = new HasConnectionPoolMetrics.Mixin() {
    @volatile private var tracker: ConnectionPoolMetrics = _
    override def connectionPoolMetrics: ConnectionPoolMetrics = this.tracker
    override def setConnectionPoolMetrics(cpm: ConnectionPoolMetrics): Unit = this.tracker = cpm
  }
}


