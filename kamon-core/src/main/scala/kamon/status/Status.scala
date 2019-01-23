package kamon.status

import com.typesafe.config.Config
import kamon.{Configuration, Environment, Kamon}
import kamon.module.Module


/**
  * Allows accessing of component's status APIs without exposing any other internal API from those components.
  */
class Status(_moduleRegistry: Module.Registry, configuration: Configuration) {

  def baseInfo(): Status.BaseInfo =
    Status.BaseInfo(BuildInfo.version, Kamon.environment, configuration.config())

  /**
    * Information about what modules have been detected in the classpath and their current status.
    */
  def moduleRegistry(): Module.Registry.Status =
    _moduleRegistry.status()
}




object Status {

  case class BaseInfo(
    version: String,
    environment: Environment,
    config: Config
  )




}
