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

import akka.actor.{ActorRef, ActorSystem, Cell}
import akka.routing.{NoRouter, RoutedActorRef}
import kamon.Kamon
import kamon.akka.Akka

case class CellInfo(path: String, isRouter: Boolean, isRoutee: Boolean, isTracked: Boolean, trackingGroups: Seq[String],
    actorCellCreation: Boolean)

object CellInfo {

  def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")

  def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): CellInfo = {
    def hasRouterProps(cell: Cell): Boolean = cell.props.deploy.routerConfig != NoRouter

    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"
    val isRouter = hasRouterProps(cell)
    val isRoutee = parent.isInstanceOf[RoutedActorRef]

    val fullPath = if (isRoutee) cellName(system, parent) else cellName(system, ref)
    val filterName = if (isRouter || isRoutee) Akka.RouterFilterName else Akka.ActorFilterName
    val isTracked = !isRootSupervisor && Kamon.filter(filterName, fullPath)
    val trackingGroups = if(isRootSupervisor) List() else Akka.actorGroups.filter(group => Kamon.filter(group, fullPath))

    CellInfo(fullPath, isRouter, isRoutee, isTracked, trackingGroups, actorCellCreation)
  }
}
