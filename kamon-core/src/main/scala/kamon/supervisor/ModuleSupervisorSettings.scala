package kamon.supervisor

import akka.actor.ActorSystem

case class AvailableModuleInfo(name: String, extensionClass: String, requiresAspectJ: Boolean, autoStart: Boolean)
case class ModuleSupervisorSettings(disableAspectJMissingWarning: Boolean, availableModules: List[AvailableModuleInfo]) {
  val modulesRequiringAspectJ = availableModules.filter(_.requiresAspectJ)
}

object ModuleSupervisorSettings {

  def apply(system: ActorSystem): ModuleSupervisorSettings = {
    import kamon.util.ConfigTools.Syntax

    val config = system.settings.config.getConfig("kamon.modules")
    val disableAspectJMissingWarning = system.settings.config.getBoolean("kamon.disable-aspectj-missing-warning")

    val modules = config.firstLevelKeys
    val availableModules = modules.map { moduleName ⇒
      val moduleConfig = config.getConfig(moduleName)

      AvailableModuleInfo(
        moduleName,
        moduleConfig.getString("extension-id"),
        moduleConfig.getBoolean("requires-aspectj"),
        moduleConfig.getBoolean("auto-start"))

    } toList

    ModuleSupervisorSettings(disableAspectJMissingWarning, availableModules)
  }

}
