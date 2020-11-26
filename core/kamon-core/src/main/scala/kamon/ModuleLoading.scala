/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

import kamon.module.Module
import kamon.module.ModuleRegistry
import kamon.module.Module.Registration

import scala.concurrent.Future

/**
  * Handles the lifecycle of all modules known by Kamon. The most common implementations of modules are metrics and
  * span reporters, but modules can be used to encapsulate any process that should be started automatically by Kamon and
  * stopped when all modules are stopped (usually during shutdown).
  *
  * Modules can be automatically discovered from the kamon.modules configuration path, using the following schema:
  *
  *   kamon.modules {
  *     module-name {
  *       enabled = true
  *       name = "My Module Name"
  *       description = "A module description"
  *       factory = "com.example.MyModuleFactory"
  *     }
  *   }
  *
  * Take a look at the reference.conf file for more details.
  *
  */
trait ModuleLoading { self: Configuration with Utilities with Metrics with Tracing =>
  protected val _moduleRegistry = new ModuleRegistry(self, clock(), self.registry(), self.tracer())
  self.onReconfigure(newConfig => self._moduleRegistry.reconfigure(newConfig))

  /**
    * Register a module instantiated by the user and returns a Registration that can be used to stop and deregister the
    * module at any time.
    */
  def registerModule(name: String, module: Module): Registration =
    _moduleRegistry.register(name, None, module)

  /**
    * Register a module instantiated by the user and returns a Registration that can be used to stop and deregister the
    * module at any time.
    */
  def registerModule(name: String, description: String, module: Module, configPath: String): Registration =
    _moduleRegistry.register(name, Some(description), module)

  /**
    * Loads modules from Kamon's configuration.
    */
  def loadModules(): Unit =
    _moduleRegistry.load(self.config())

  /**
    * Stops all registered modules and returns a future that completes when the stop callback on all available modules
    * have been completed. This includes automatically and programmatically registered modules.
    */
  def stopModules(): Future[Unit] =
    _moduleRegistry.stopModules()

}
