package kamon.status

import com.typesafe.config.Config
import kamon.metric.InstrumentFactory.InstrumentType
import kamon.metric.MetricRegistry
import kamon.{Configuration, Environment, Kamon}
import kamon.module.ModuleRegistry
import kamon.module.Module.{Kind => ModuleKind}

/**
  * Allows accessing of component's status APIs without exposing any other internal API from those components.
  */
class Status(_moduleRegistry: ModuleRegistry, _metricRegistry: MetricRegistry, configuration: Configuration) {

  def settings(): Status.Settings =
    Status.Settings(BuildInfo.version, Kamon.environment, configuration.config())

  /**
    * Information about what modules have been detected in the classpath and their current status.
    */
  def moduleRegistry(): Status.ModuleRegistry =
    _moduleRegistry.status()

  def metricRegistry(): Status.MetricRegistry =
    _metricRegistry.status()
}



object Status {

  case class Settings(
    version: String,
    environment: Environment,
    config: Config
  )


  case class ModuleRegistry(
    modules: Seq[Module]
  )

  case class Module(
    name: String,
    description: String,
    kind: ModuleKind,
    isProgrammaticallyRegistered: Boolean,
    isStarted: Boolean
  )

  case class MetricRegistry(
    metrics: Seq[Metric]
  )

  case class Metric(
    name: String,
    tags: Map[String, String],
    instrumentType: InstrumentType
  )
}
