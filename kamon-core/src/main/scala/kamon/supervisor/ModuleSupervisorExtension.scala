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

import akka.actor
import akka.actor._
import kamon.Kamon
import kamon.supervisor.KamonSupervisor.CreateModule

import scala.concurrent.{ Promise, Future }
import scala.util.Success

object ModuleSupervisor extends ExtensionId[ModuleSupervisorExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = ModuleSupervisor
  def createExtension(system: ExtendedActorSystem): ModuleSupervisorExtension = new ModuleSupervisorExtensionImpl(system)
}

trait ModuleSupervisorExtension extends actor.Extension {
  def createModule(name: String, props: Props): Future[ActorRef]
}

class ModuleSupervisorExtensionImpl(system: ExtendedActorSystem) extends ModuleSupervisorExtension {
  import system.dispatcher

  private val _settings = ModuleSupervisorSettings(system)
  private val _supervisor = system.actorOf(KamonSupervisor.props(_settings, system.dynamicAccess), "kamon")

  def createModule(name: String, props: Props): Future[ActorRef] = Future {} flatMap { _: Unit ⇒
    val modulePromise = Promise[ActorRef]()
    _supervisor ! CreateModule(name, props, modulePromise)
    modulePromise.future
  }
}

class KamonSupervisor(settings: ModuleSupervisorSettings, dynamicAccess: DynamicAccess) extends Actor with ActorLogging {

  init()

  def receive = {
    case CreateModule(name, props, childPromise) ⇒ createChildModule(name, props, childPromise)
  }

  def createChildModule(name: String, props: Props, childPromise: Promise[ActorRef]): Unit =
    context.child(name).map { alreadyAvailableModule ⇒
      log.warning("Received a request to create module [{}] but the module is already available, returning the existent instance.")
      childPromise.complete(Success(alreadyAvailableModule))

    } getOrElse (childPromise.complete(Success(context.actorOf(props, name))))

  def init(): Unit = {
    if (settings.modulesRequiringAspectJ.nonEmpty && !isAspectJPresent && settings.showAspectJMissingWarning)
      logAspectJWeaverMissing(settings.modulesRequiringAspectJ)

    // Force initialization of all modules marked with auto-start.
    settings.availableModules.filter(_.autoStart).foreach { module ⇒
      if (module.extensionClass == "none")
        log.debug("Ignoring auto start of the [{}] module with no extension class.")
      else
        dynamicAccess.getObjectFor[ExtensionId[Kamon.Extension]](module.extensionClass).map { moduleID ⇒
          moduleID.get(context.system)
          log.debug("Auto starting the [{}] module.", module.name)

        } recover {
          case th: Throwable ⇒ log.error(th, "Failed to auto start the [{}] module.", module.name)
        }

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

object KamonSupervisor {
  case class CreateModule(name: String, props: Props, childPromise: Promise[ActorRef])

  def props(settings: ModuleSupervisorSettings, dynamicAccess: DynamicAccess): Props =
    Props(new KamonSupervisor(settings, dynamicAccess))

}

