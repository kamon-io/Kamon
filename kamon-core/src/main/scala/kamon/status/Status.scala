package kamon.status

import com.typesafe.config.Config
import kamon.metric.MetricRegistry
import kamon.{Configuration, Environment, Kamon}
import kamon.module.Module


/**
  * Allows accessing of component's status APIs without exposing any other internal API from those components.
  */
class Status(_moduleRegistry: Module.Registry, _metricRegistry: MetricRegistry, configuration: Configuration) {

  def baseInfo(): Status.BaseInfo =
    Status.BaseInfo(BuildInfo.version, Kamon.environment, configuration.config())

  /**
    * Information about what modules have been detected in the classpath and their current status.
    */
  def moduleRegistry(): Module.Registry.Status =
    _moduleRegistry.status()

  def metricRegistry(): MetricRegistry.Status =
    _metricRegistry.status()
}



object Status {

  case class BaseInfo(
    version: String,
    environment: Environment,
    config: Config
  )




}
