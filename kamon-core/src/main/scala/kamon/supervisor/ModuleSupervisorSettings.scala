/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

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
