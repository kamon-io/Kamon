package akka.kamon.instrumentation

import akka.actor.{ Cell, ActorRef, ActorSystem }
import akka.routing.{ NoRouter, RoutedActorRef, RoutedActorCell }
import kamon.Kamon
import kamon.akka.{ ActorMetrics, ActorGroupConfig, RouterMetrics }
import kamon.metric.Entity

case class CellInfo(entity: Entity, isRouter: Boolean, isRoutee: Boolean,
    isTracked: Boolean, trackingGroups: List[String], actorCellCreation: Boolean)

object CellInfo {

  def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")

  def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): CellInfo = {
    import kamon.metric.Entity
    def hasRouterProps(cell: Cell): Boolean = cell.props.deploy.routerConfig != NoRouter

    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"
    val isRouter = hasRouterProps(cell)
    val isRoutee = parent.isInstanceOf[RoutedActorRef]

    val name = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val category = if (isRouter || isRoutee) RouterMetrics.category else ActorMetrics.category
    val entity = Entity(name, category)
    val isTracked = !isRootSupervisor && Kamon.metrics.shouldTrack(entity)
    val trackingGroups = if(isRootSupervisor) List() else ActorGroupConfig.actorShouldBeTrackedUnderGroups(name)

    CellInfo(entity, isRouter, isRoutee, isTracked, trackingGroups, actorCellCreation)
  }
}
