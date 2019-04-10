package kamon.status

import com.typesafe.config.Config
import kamon.metric.{MeasurementUnit, MetricRegistry}
import kamon.{Configuration, Kamon}
import kamon.module.ModuleRegistry
import kamon.module.Module.{Kind => ModuleKind}
import java.util.{Collections, List => JavaList, Map => JavaMap}

import kamon.tag.TagSet

/**
  * Exposes Kamon components' status information. This is meant to be used for informational and debugging purposes and
  * by no means should replace the use of reporters to extract information from Kamon.
  */
class Status(_moduleRegistry: ModuleRegistry, _metricRegistry: MetricRegistry, configuration: Configuration) {

  /**
    * Settings currently used by Kamon.
    */
  def settings(): Status.Settings =
    Status.Settings(BuildInfo.version, Kamon.environment, configuration.config())

  /**
    * Status of the module registry. Describes what modules have been detected and registered, either from the classpath
    * or programmatically and their current status.
    */
  def moduleRegistry(): Status.ModuleRegistry =
    _moduleRegistry.status()

  /**
    * Status of the metric registry. Describes all metrics currently tracked by Kamon.
    */
  def metricRegistry(): Status.MetricRegistry =
    _metricRegistry.status()


  /**
    * Status of instrumentation modules that have been detected and/or loaded into the current JVM. Read the
    * [[Status.Instrumentation]] companion object's docs for more information.
    */
  def instrumentation(): Status.Instrumentation = {
    import Status.Instrumentation._

    Status.Instrumentation(
      isActive(),
      modules(),
      errors()
    )
  }
}



object Status {

  /** Describes the global settings currently being used by Kamon */
  case class Settings(
    version: String,
    environment: Environment,
    config: Config
  )

  /** Describes all modules currently know to Kamon's module registry. */
  case class ModuleRegistry(
    modules: Seq[Module]
  )

  /** Describes all known information for a single module.  */
  case class Module(
    name: String,
    description: String,
    clazz: String,
    kind: ModuleKind,
    programmaticallyRegistered: Boolean,
    enabled: Boolean,
    started: Boolean
  )

  /** Describes all metrics currently registered on Kamon's default metric registry */
  case class MetricRegistry(
    metrics: Seq[Metric]
  )

  /**
    * Describes a metric from a metric registry. Contains the basic metric information and details on all instruments
    * registered for that metric.
    */
  case class Metric (
    name: String,
    description: String,
    unit: MeasurementUnit,
    instrumentType: kamon.metric.Instrument.Type,
    instruments: Seq[Instrument]
  )

  /** Describes the combination of tags in any given instrument */
  case class Instrument (
    tags: TagSet
  )

  /**
    * Describes all known instrumentation modules. This data is completely untyped and not expected to be used anywhere
    * outside Kamon. The data is injected on runtime by the Kanela instrumentation agent.
    */
  case class Instrumentation(
    active: Boolean,
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
  object Instrumentation {

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
