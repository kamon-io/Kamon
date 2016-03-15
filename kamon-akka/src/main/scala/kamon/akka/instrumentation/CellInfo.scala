package akka.kamon.instrumentation

import akka.actor.{ Cell, ActorRef, ActorSystem }
import akka.routing.{ RoutedActorRef, RoutedActorCell }
import kamon.Kamon
import kamon.akka.{ ActorMetrics, RouterMetrics }
import kamon.metric.Entity

case class CellInfo(entity: Entity, isRouter: Boolean, isRoutee: Boolean, isTracked: Boolean)

object CellInfo {

  def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")

  def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): CellInfo = {
    import kamon.metric.Entity

    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"
    val isRouter = cell.isInstanceOf[RoutedActorCell]
    val isRoutee = parent.isInstanceOf[RoutedActorRef]

    val name = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val category = if (isRouter || isRoutee) RouterMetrics.category else ActorMetrics.category
    val entity = Entity(name, category)
    val isTracked = !isRootSupervisor && Kamon.metrics.shouldTrack(entity)

    CellInfo(entity, isRouter, isRoutee, isTracked)
  }
}