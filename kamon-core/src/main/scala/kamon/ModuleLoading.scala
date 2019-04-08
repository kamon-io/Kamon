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
  *       description = "A module description"
  *       kind = "combined | metric | span | plain"
  *       class = "com.example.MyModule"
  *     }
  *   }
  *
  */
trait ModuleLoading { self: ClassLoading with Configuration with Utilities with Metrics with Tracing =>
  protected val _moduleRegistry = new ModuleRegistry(self, self, clock(), self.registry(), self.tracer())
  self.onReconfigure(newConfig => self._moduleRegistry.reconfigure(newConfig))


  /**
    * Register a module instantiated by the user.
    *
    * @param name Module name. Registration will fail if a module with the given name already exists.
    * @param module The module instance.
    * @return A Registration that can be used to de-register the module at any time.
    */
  def registerModule(name: String, module: Module): Registration =
    _moduleRegistry.register(name, module)

  /**
    * Loads modules from Kamon's configuration.
    */
  def loadModules(): Unit =
    _moduleRegistry.load(self.config())

  /**
    * Stops all registered modules. This includes automatically and programmatically registered modules.
    *
    * @return A future that completes when the stop callback on all available modules have been completed.
    */
  def stopModules(): Future[Unit] =
    _moduleRegistry.stop()

}
