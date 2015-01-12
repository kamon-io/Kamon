package kamon

import _root_.akka.actor
import _root_.akka.actor._
import kamon.ModuleSupervisor.CreateModule

import scala.concurrent.{ Future, Promise }
import scala.util.Success

object ModuleSupervisor extends ExtensionId[ModuleSupervisorExtension] with ExtensionIdProvider {

  def lookup(): ExtensionId[_ <: actor.Extension] = ModuleSupervisor
  def createExtension(system: ExtendedActorSystem): ModuleSupervisorExtension = new ModuleSupervisorExtensionImpl(system)

  case class CreateModule(name: String, props: Props, childPromise: Promise[ActorRef])
}

trait ModuleSupervisorExtension extends actor.Extension {
  def createModule(name: String, props: Props): Future[ActorRef]
}

class ModuleSupervisorExtensionImpl(system: ExtendedActorSystem) extends ModuleSupervisorExtension {
  import system.dispatcher
  private lazy val supervisor = system.actorOf(Props[ModuleSupervisor], "kamon")

  def createModule(name: String, props: Props): Future[ActorRef] = Future {} flatMap { _: Unit ⇒
    val modulePromise = Promise[ActorRef]()
    supervisor ! CreateModule(name, props, modulePromise)
    modulePromise.future
  }
}

class ModuleSupervisor extends Actor with ActorLogging {

  def receive = {
    case CreateModule(name, props, childPromise) ⇒ createChildModule(name, props, childPromise)
  }

  def createChildModule(name: String, props: Props, childPromise: Promise[ActorRef]): Unit = {
    context.child(name).map { alreadyAvailableModule ⇒
      log.warning("Received a request to create module [{}] but the module is already available, returning the existent one.")
      childPromise.complete(Success(alreadyAvailableModule))

    } getOrElse {
      childPromise.complete(Success(context.actorOf(props, name)))
    }
  }
}
