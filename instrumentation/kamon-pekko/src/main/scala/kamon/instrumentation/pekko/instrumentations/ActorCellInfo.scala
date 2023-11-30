package kamon.instrumentation.pekko.instrumentations

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.routing.{BalancingPool, NoRouter}

import scala.language.existentials

/**
  * Basic information that should be read from an ActorCell for instrumentation purposes.
  */
case class ActorCellInfo (
  path: String,
  name: String,
  systemName: String,
  dispatcherName: String,
  isRouter: Boolean,
  isRoutee: Boolean,
  isRootSupervisor: Boolean,
  isStreamImplementationActor: Boolean,
  isTemporary: Boolean,
  actorOrRouterClass: Class[_],
  routeeClass: Option[Class[_]]
)

object ActorCellInfo {

  private val StreamsSupervisorActorClassName = "org.apache.pekko.stream.impl.StreamSupervisor"
  private val StreamsInterpreterActorClassName = "org.apache.pekko.stream.impl.fusing.ActorGraphInterpreter"

  /**
    * Reads information from an ActorCell.
    */
  def from(cell: Any, ref: ActorRef, parent: ActorRef, system: ActorSystem): ActorCellInfo = {
    val props = PekkoPrivateAccess.cellProps(cell).get
    val actorName = ref.path.name

    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"
    val isRouter = hasRouterProps(props)
    val isRoutee = PekkoPrivateAccess.isRoutedActorRef(parent)
    val isTemporary = PekkoPrivateAccess.isUnstartedActorCell(cell)

    val (actorOrRouterClass, routeeClass) =
      if(isRouter)
        (props.routerConfig.getClass, Some(ref.asInstanceOf[HasRouterProps].routeeProps.actorClass()))
      else if (isRoutee)
        (parent.asInstanceOf[HasRouterProps].routerProps.routerConfig.getClass, Some(props.actorClass()))
      else
        (props.actorClass(), None)

    val fullPath = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val dispatcherName = if(isRouter) {
      if(props.routerConfig.isInstanceOf[BalancingPool]) {

        // Even though the router actor for a BalancingPool can have a different dispatcher we will
        // assign the name of the same dispatcher where the routees will run to ensure all metrics are
        // correlated and cleaned up correctly.
        val deployPath = ref.path.elements.drop(1).mkString("/", "/", "")
        "BalancingPool-" + deployPath

      } else {

        // It might happen that the deployment configuration will provide a different dispatcher name
        // for the routees and we should catch that case only when creating the router (the routees will
        // be initialized with an updated Props instance.
        PekkoPrivateAccess.lookupDeploy(ref.path, system).map(_.dispatcher).getOrElse(props.dispatcher)
      }
    } else props.dispatcher

    val actorClassName = actorOrRouterClass.getName
    val isStreamImplementationActor =
      actorClassName == StreamsSupervisorActorClassName || actorClassName == StreamsInterpreterActorClassName

    ActorCellInfo(fullPath, actorName, system.name, dispatcherName, isRouter, isRoutee, isRootSupervisor,
      isStreamImplementationActor, isTemporary, actorOrRouterClass, routeeClass)
  }

  /**
    * Returns a simple Class name, working around issues that might arise when using double nested classes in Scala.
    */
  def simpleClassName(cls: Class[_]): String = {
    // Class.getSimpleName could fail if called on a double-nested class.
    // See https://github.com/scala/bug/issues/2034 for more details.
    try { cls.getSimpleName } catch { case _: Throwable => {
      val className = cls.getName
      val lastSeparator = className.lastIndexOf('.')

      if(lastSeparator > 0)
        className.substring(lastSeparator + 1)
      else
        className
    }}
  }

  def isTyped(className: Class[_]): Boolean = {
    simpleClassName(className) == "ActorAdapter"
  }

  private def hasRouterProps(props: Props): Boolean =
    props.deploy.routerConfig != NoRouter

  private def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")
}
