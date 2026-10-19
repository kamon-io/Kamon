/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package module

import com.typesafe.config.Config
import kamon.util.Filter

import java.time.Duration
import scala.concurrent.ExecutionContext

/**
  * Modules provide additional capabilities to Kamon, like collecting JVM metrics or exporting the metrics and trace
  * data to external services. Additionally, modules can be automatically registered in Kamon by simply being present
  * in the classpath and having the appropriate entry in the configuration file. All modules get a dedicated execution
  * context which will be used to call the stop and reconfigure hooks, as well as any data processing callbacks.
  *
  * Besides the basic lifecycle hooks, when registering a [[MetricReporter]] and/or [[SpanReporter]] module, Kamon will
  * also schedule calls to [[MetricReporter.reportPeriodSnapshot()]] and [[SpanReporter.reportSpans()]] in the module's
  * execution context.
  */
trait Module {

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
  * Creates an instance of a module.
  */
trait ModuleFactory {

  def create(settings: ModuleFactory.Settings): Module

}

object ModuleFactory {

  /**
    * Initial settings that get passed into factories when creating an automatically detected module.
    */
  case class Settings(
    config: Config,
    executionContext: ExecutionContext
  )
}

/**
  * Modules implementing this trait will get registered for periodically receiving metric period snapshots and span
  * batches.
  */
trait CombinedReporter extends MetricReporter with SpanReporter

object Module {

  sealed trait Kind
  object Kind {
    case object MetricsReporter extends Kind
    case object SpansReporter extends Kind
    case object CombinedReporter extends Kind
    case object ScheduledAction extends Kind
    case object Unknown extends Kind
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
    * Preserves information about an original module that has been wrapped with transformations.
    */
  trait Wrapped extends Module {
    def originalClass: Class[_ <: Module]
  }

  /**
    * Configuration of a given module present in the classpath.
    *
    * @param name Module's name
    * @param description Module's description.
    * @param enabled Whether the module is enabled or not. Enabled modules in the classpath will be automatically
    *                started in any call to Kamon.loadModules().
    * @param factory FQCN of the ModuleFactory implementation for the module.
    */
  case class Settings(
    name: String,
    description: String,
    enabled: Boolean,
    factory: Option[String],
    metricsFilter: Option[Filter],
    collectInterval: Option[Duration]
  )
}
