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

package kamon

import _root_.akka.actor
import _root_.akka.actor._
import _root_.akka.event.Logging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Pointcut, Aspect }

private[kamon] object ModuleLoader extends ExtensionId[ModuleLoaderExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = ModuleLoader
  def createExtension(system: ExtendedActorSystem): ModuleLoaderExtension = new ModuleLoaderExtension(system)
}

private[kamon] class ModuleLoaderExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, "ModuleLoader")
  val settings = ModuleLoaderSettings(system)

  if (settings.modulesRequiringAspectJ.nonEmpty && !isAspectJPresent && settings.showAspectJMissingWarning)
    logAspectJWeaverMissing(settings.modulesRequiringAspectJ)

  // Force initialization of all modules marked with auto-start.
  settings.availableModules.filter(_.autoStart).foreach { module ⇒
    if (module.extensionClass == "none")
      log.debug("Ignoring auto start of the [{}] module with no extension class.", module.name)
    else
      system.dynamicAccess.getObjectFor[ExtensionId[Kamon.Extension]](module.extensionClass).map { moduleID ⇒
        log.debug("Auto starting the [{}] module.", module.name)
        moduleID.get(system)

      } recover {
        case th: Throwable ⇒ log.error(th, "Failed to auto start the [{}] module.", module.name)
      }

  }

  // When AspectJ is present the kamon.supervisor.AspectJPresent aspect will make this return true.
  def isAspectJPresent: Boolean = false

  def logAspectJWeaverMissing(modulesRequiringAspectJ: List[AvailableModuleInfo]): Unit = {
    val moduleNames = modulesRequiringAspectJ.map(_.name).mkString(", ")
    val weaverMissingMessage =
      """
        |
        |  ___                           _      ___   _    _                                 ___  ___ _            _
        | / _ \                         | |    |_  | | |  | |                                |  \/  |(_)          (_)
        |/ /_\ \ ___  _ __    ___   ___ | |_     | | | |  | |  ___   __ _ __   __ ___  _ __  | .  . | _  ___  ___  _  _ __    __ _
        ||  _  |/ __|| '_ \  / _ \ / __|| __|    | | | |/\| | / _ \ / _` |\ \ / // _ \| '__| | |\/| || |/ __|/ __|| || '_ \  / _` |
        || | | |\__ \| |_) ||  __/| (__ | |_ /\__/ / \  /\  /|  __/| (_| | \ V /|  __/| |    | |  | || |\__ \\__ \| || | | || (_| |
        |\_| |_/|___/| .__/  \___| \___| \__|\____/   \/  \/  \___| \__,_|  \_/  \___||_|    \_|  |_/|_||___/|___/|_||_| |_| \__, |
        |            | |                                                                                                      __/ |
        |            |_|                                                                                                     |___/
        |
        | It seems like your application was not started with the -javaagent:/path-to-aspectj-weaver.jar option but Kamon detected
        | the following modules which require AspecJ to work properly:
        |
      """.stripMargin + moduleNames +
        """
          |
          | If you need help on setting up the aspectj weaver go to http://kamon.io/introduction/get-started/ for more info. On the
          | other hand, if you are sure that you do not need or do not want to use the weaver then you can disable this error message
          | by changing the kamon.show-aspectj-missing-warning setting in your configuration file.
          |
        """.stripMargin

    log.error(weaverMissingMessage)
  }
}

private[kamon] case class AvailableModuleInfo(name: String, extensionClass: String, requiresAspectJ: Boolean, autoStart: Boolean)
private[kamon] case class ModuleLoaderSettings(showAspectJMissingWarning: Boolean, availableModules: List[AvailableModuleInfo]) {
  val modulesRequiringAspectJ = availableModules.filter(_.requiresAspectJ)
}

private[kamon] object ModuleLoaderSettings {

  def apply(system: ActorSystem): ModuleLoaderSettings = {
    import kamon.util.ConfigTools.Syntax

    val config = system.settings.config.getConfig("kamon.modules")
    val showAspectJMissingWarning = system.settings.config.getBoolean("kamon.show-aspectj-missing-warning")

    val modules = config.firstLevelKeys
    val availableModules = modules.map { moduleName ⇒
      val moduleConfig = config.getConfig(moduleName)

      AvailableModuleInfo(
        moduleName,
        moduleConfig.getString("extension-id"),
        moduleConfig.getBoolean("requires-aspectj"),
        moduleConfig.getBoolean("auto-start"))

    } toList

    ModuleLoaderSettings(showAspectJMissingWarning, availableModules)
  }
}

@Aspect
private[kamon] class AspectJPresent {

  @Pointcut("execution(* kamon.ModuleLoaderExtension.isAspectJPresent())")
  def isAspectJPresentAtModuleSupervisor(): Unit = {}

  @Around("isAspectJPresentAtModuleSupervisor()")
  def aroundIsAspectJPresentAtModuleSupervisor(pjp: ProceedingJoinPoint): Boolean = true

}
