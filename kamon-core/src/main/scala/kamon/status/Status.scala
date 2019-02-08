package kamon.status

import com.typesafe.config.Config
import kamon.metric.InstrumentFactory.InstrumentType
import kamon.metric.{MeasurementUnit, MetricRegistry}
import kamon.{Configuration, Environment, Kamon}
import kamon.module.ModuleRegistry
import kamon.module.Module.{Kind => ModuleKind}
import java.util.{Collections, List => JavaList, Map => JavaMap}

/**
  * Exposes Kamon components' status information. This is meant to be used for informational and debugging purposes.
  */
class Status(_moduleRegistry: ModuleRegistry, _metricRegistry: MetricRegistry, configuration: Configuration) {

  /**
    * Settings currently used by Kamon.
    */
  def settings(): Status.Settings =
    Status.Settings(BuildInfo.version, Kamon.environment, configuration.config())

  /**
    * Status of the module registry. Describes what modules have been detected in the classpath and their current
    * statuses.
    */
  def moduleRegistry(): Status.ModuleRegistry =
    _moduleRegistry.status()

  /**
    * Status of the metric registry. Describes all metrics currently tracked by Kamon.
    */
  def metricRegistry(): Status.MetricRegistry =
    _metricRegistry.status()


  /**
    * PRIVATE API.
    *
    * Status of instrumentation modules that have been detected and/or loaded into the current JVM. This
    * API is not meant to be used by the general public.
    *
    * Read the [[Status.Instrumentation]] companion object's docs for more information.
    */
  private[kamon] def instrumentation(): Status.Instrumentation = {
    import Status.Instrumentation._

    Status.Instrumentation(
      isActive(),
      modules(),
      errors()
    )
  }
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
    clazz: String,
    kind: ModuleKind,
    isProgrammaticallyRegistered: Boolean,
    isEnabled: Boolean,
    isStarted: Boolean
  )

  case class MetricRegistry(
    metrics: Seq[Metric]
  )

  case class Metric(
    name: String,
    tags: Map[String, String],
    unit: MeasurementUnit,
    instrumentType: InstrumentType
  )


  /**
    * Status of the instrumentation modules. This data is completely untyped and not expected to be used anywhere
    * outside Kamon.
    */
  private[kamon] case class Instrumentation(
    isIActive: Boolean,
    modules: JavaMap[String, String],
    errors: JavaMap[String, JavaList[Throwable]]
  )


  /**
    * This object works as a bridge between Kamon and Kanela to gather information about instrumentation modules. When
    * instrumentation is enabled, it should replace the implementation of the members of this object and return proper
    * information.
    *
    * This data is only exposed directly to the status page API because it lacks any sort of type safety. We might
    * change this in the future and provide proper types for all instrumentation modules' info.
    */
  private[kamon] object Instrumentation {

    /**
      * Whether instrumentation is active or not. When Kanela is present it will replace this method to return true.
      */
    def isActive(): java.lang.Boolean =
      false

    /**
      * List all instrumentation modules known and their current status. The result map contains the module name as keys
      * and a JSON representation of the module status as values. The expected structure in the JSON representations is
      * as follows:
      *
      *   {
      *     'description': 'A explicative module description',
      *     'isEnabled': true | false,
      *     'isActive': true | false
      *   }
      *
      *  The "isEnabled" flag tells whether the module is able to instrument classes or not. By default, all modules are
      *  able to instrument classes but some modules might be shipped in a disabled state or forced to be disabled via
      *  configuration.
      *
      *  The "isActive" flag tells whether the modules has already applied instrumentation to any of its target classes.
      *
      */
    def modules(): JavaMap[String, String] =
      Collections.emptyMap()


    /**
      * List all errors that might have happened during the instrumentation initialization. The resulting map contains
      * a list of modules and any exceptions thrown by them during initialization. If not exceptions are thrown the map
      * will always be empty.
      */
    def errors(): JavaMap[String, JavaList[Throwable]] =
      Collections.emptyMap()
  }
}
