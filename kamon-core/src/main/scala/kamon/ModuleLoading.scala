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
  * Modules can be automatically discovered from the kamon.modules configuration key, using the following schema:
  *
  *   kamon.modules {
  *     module-name {
  *       enabled = true
  *       name = "My Module Name"
  *       description = "A module description"
  *       factory = "com.example.MyModuleFactory"
  *       config-path = "kamon.my-module"
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
    * Register a module instantiated by the user. This method assumes that the module does not read any dedicated
    * configuration settings and when Kamon gets reconfigured, the module will be provided with an empty Config.
    *
    * The returned registration can be used to stop and deregister the module at any time.
    */
  def registerModule(name: String, module: Module): Registration =
    _moduleRegistry.register(name, module, None)

  /**
    * Register a module instantiated by the user. When Kamon is reconfigured, the module will be reconfigured with the
    * settings found under the provided configPath.
    *
    * The returned registration can be used to stop and deregister the module at any time.
    */
  def registerModule(name: String, module: Module, configPath: String): Registration =
    _moduleRegistry.register(name, module, Option(configPath))

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
    _moduleRegistry.stop()

}
