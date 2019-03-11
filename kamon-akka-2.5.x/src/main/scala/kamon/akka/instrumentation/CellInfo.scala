/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
package akka.kamon.instrumentation

import akka.actor.{ActorRef, ActorSystem, Cell, Deployer, ExtendedActorSystem}
import akka.routing.{BalancingPool, NoRouter, RoutedActorRef}
import kamon.Kamon
import kamon.akka.Akka

import scala.language.existentials

case class CellInfo(path: String, isRouter: Boolean, isRoutee: Boolean, isTracked: Boolean, trackingGroups: Seq[String],
    actorCellCreation: Boolean, systemName: String, dispatcherName: String, isTraced: Boolean, actorOrRouterClass: Class[_],
    routeeClass: Option[Class[_]], actorName: String)

object CellInfo {

  def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")

  def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): CellInfo = {
    def hasRouterProps(cell: Cell): Boolean = cell.props.deploy.routerConfig != NoRouter

    val actorName = ref.path.name

    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"
    val isRouter = hasRouterProps(cell)
    val isRoutee = parent.isInstanceOf[RoutedActorRef]

    val (actorOrRouterClass, routeeClass) =
      if(isRouter)
        (cell.props.routerConfig.getClass, Some(ref.asInstanceOf[RoutedActorRefAccessor].routeeProps.actorClass))
      else if (isRoutee)
        (parent.asInstanceOf[RoutedActorRefAccessor].routerProps.routerConfig.getClass, Some(cell.props.actorClass))
      else
        (cell.props.actorClass(), None)

    val fullPath = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val filterName = if (isRouter || isRoutee) Akka.RouterFilterName else Akka.ActorFilterName
    val isTracked = !isRootSupervisor && Akka.filters.get(filterName).fold(false)(_.accept(fullPath))
    val isTraced = Akka.filters.get(Akka.ActorTracingFilterName).fold(false)(_.accept(fullPath))
    val trackingGroups = if(isRootSupervisor) List() else Akka.actorGroups(fullPath)

    val dispatcherName = if(isRouter) {
      if(cell.props.routerConfig.isInstanceOf[BalancingPool]) {
        // Even though the router actor for a BalancingPool can have a different dispatcher we will
        // assign the name of the same dispatcher where the routees will run to ensure all metrics are
        // correlated and cleaned up correctly.
        val deployPath = ref.path.elements.drop(1).mkString("/", "/", "")
        "BalancingPool-" + deployPath

      } else {
        // It might happen that the deployment configuration will provide a different dispatcher name
        // for the routees and we should catch that case only when creating the router (the routees will
        // be initialized with an updated Props instance.
        val deployer = new Deployer(system.settings, system.asInstanceOf[ExtendedActorSystem].dynamicAccess)
        deployer.lookup(ref.path / "$a").map(_.dispatcher).getOrElse(cell.props.dispatcher)
      }
    } else cell.props.dispatcher

    CellInfo(fullPath, isRouter, isRoutee, isTracked, trackingGroups, actorCellCreation, system.name, dispatcherName,
      isTraced, actorOrRouterClass, routeeClass, actorName)
  }
}
