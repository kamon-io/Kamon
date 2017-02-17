package kamon.jdbc.instrumentation

import kamon.jdbc.metric.ConnectionPoolMetrics

class ConnectionPoolTracker(val recorder: ConnectionPoolMetrics) {
  def connectionOpened(): Unit =
    recorder.openConnections.increment()

  def connectionClosed(): Unit =
    recorder.openConnections.decrement()

  def connectionBorrowed(): Unit =
    recorder.borrowedConnections.increment()

  def connectionReturned(): Unit =
    recorder.borrowedConnections.decrement()
}

trait HasConnectionPoolTracker {
  def connectionPoolTracker: ConnectionPoolTracker
  def setConnectionPoolTracker(cpt: ConnectionPoolTracker): Unit
}

object HasConnectionPoolTracker {
  def apply(): HasConnectionPoolTracker = new HasConnectionPoolTracker() {
    @volatile private var tracker: ConnectionPoolTracker = _
    override def connectionPoolTracker: ConnectionPoolTracker = this.tracker
    override def setConnectionPoolTracker(cpt: ConnectionPoolTracker): Unit = this.tracker = cpt
  }
}

