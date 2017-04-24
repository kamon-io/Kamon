package kamon

import kamon.metric.MetricsSubscriber

trait Subscriptions {
  def loadFromConfig()
  def subscribeToMetrics(subscriber: MetricsSubscriber): Subscription
}

trait Subscription {
  def cancel(): Unit
}
