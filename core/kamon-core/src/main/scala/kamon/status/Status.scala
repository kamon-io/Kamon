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

package kamon.status

import com.typesafe.config.Config
import kamon.metric.{MeasurementUnit, MetricRegistry}
import kamon.{Configuration, Kamon}
import kamon.module.ModuleRegistry
import kamon.module.Module.{Kind => ModuleKind}
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
    * Status of instrumentation modules that have been detected and/or loaded into the current JVM. The implementation
    * of this method is closely tied to the Kanela instrumentation agent. Read the Status.Instrumentation's companion
    * object docs for more information.
    */
  def instrumentation(): Status.Instrumentation =
    InstrumentationStatus.create(warnIfFailed = false)

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
  case class Instrumentation (
    present: Boolean,
    kanelaVersion: Option[String],
    modules: Seq[Status.Instrumentation.ModuleInfo],
    errors: Seq[Status.Instrumentation.TypeError]
  )

  object Instrumentation {

    /**
      * Describes an instrumentation module loaded by Kanela. Besides name and description, the "enabled" flag tells
      * whether the module is able to instrument classes or not. By default, all modules are able to instrument classes
      * but some modules might be shipped in a disabled state or forced to be disabled via configuration. The "active"
      * flag tells whether the modules has already applied instrumentation to any of its target types.
      */
    case class ModuleInfo (
      path: String,
      name: String,
      description: String,
      enabled: Boolean,
      active: Boolean
    )

    /**
      * Describes errors that might have occurred while transforming a target type.
      */
    case class TypeError (
      targetType: String,
      errors: Seq[Throwable]
    )
  }
}
