package kamon
package module

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.trace.Span

/**
  * Modules provide additional capabilities to Kamon, like collecting JVM metrics or exporting the metrics and trace
  * data to external services. Additionally, modules can be automatically registered in Kamon by simply being present
  * in the classpath and having the appropriate entry in the configuration file. All modules get a dedicated execution
  * context which will be used to call the start, stop and reconfigure hooks.
  *
  * Besides the basic lifecycle hooks, when registering a [[MetricReporter]] and/or [[SpanReporter]] module, Kamon will
  * also schedule calls to [[MetricReporter.reportPeriodSnapshot()]] and [[SpanReporter.reportSpans()]] in the module's
  * execution context.
  */
trait Module {

  /**
    * Signals that a the module has been registered in Kamon's module registry.
    */
  def start(): Unit

  /**
    * Signals that the module should be stopped and all acquired resources, if any, should be released.
    */
  def stop(): Unit

  /**
    * Signals that a new configuration object has been provided to Kamon. Modules should ensure that their internal
    * settings are in sync with the provided configuration.
    */
  def reconfigure(newConfig: Config): Unit
}


/**
  * Modules implementing this trait will get registered for periodically receiving metric period snapshots. The
  * frequency of the period snapshots is controlled by the kamon.metric.tick-interval setting.
  */
trait MetricReporter extends Module {
  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit
}

/**
  * Modules implementing this trait will get registered for periodically receiving span batches. The frequency of the
  * span batches is controlled by the kamon.trace.tick-interval setting.
  */
trait SpanReporter extends Module {
  def reportSpans(spans: Seq[Span.FinishedSpan]): Unit
}

/**
  * Modules implementing this trait will get registered for periodically receiving metric period snapshots and span
  * batches.
  */
trait CombinedReporter extends MetricReporter with SpanReporter


object Module {

  sealed trait Kind
  object Kind {
    case object Combined extends Kind
    case object Metric extends Kind
    case object Span extends Kind
    case object Plain extends Kind
  }

  /**
    * Represents a module's registration on the module registry. A module can be stopped at any time by cancelling its
    * registration.
    */
  trait Registration {

    /**
      * Removes and stops the related module.
      */
    def cancel(): Unit
  }

  /**
    * Configuration of a given module present in the classpath.
    *
    * @param name Module's name
    * @param description Module's description.
    * @param clazz The class implementing the configured module.
    * @param kind Module kind.
    * @param enabled Whether the module is enabled or not. Enabled modules in the classpath will be automatically
    *                started in any call to Kamon.loadModules().
    */
  case class Settings(
    name: String,
    description: String,
    clazz: Class[_ <: Module],
    kind: Module.Kind,
    enabled: Boolean
  )
}