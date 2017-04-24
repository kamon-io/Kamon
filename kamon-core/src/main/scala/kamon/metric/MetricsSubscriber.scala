package kamon.metric

import com.typesafe.config.Config

trait MetricsSubscriber {
  def reconfigure(config: Config): Unit

  def start(config: Config): Unit
  def shutdown(): Unit

  def processTick(snapshot: String)
}
