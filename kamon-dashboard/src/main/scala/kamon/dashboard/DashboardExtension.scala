/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.dashboard

import akka.actor._
import akka.io.IO
import spray.can.Http

object DashboardExtension extends ExtensionId[DashboardExtensionImpl] with ExtensionIdProvider {
  override def lookup = DashboardExtension
  override def createExtension(system: ExtendedActorSystem) = new DashboardExtensionImpl(system)
}

class DashboardExtensionImpl(system: ExtendedActorSystem) extends Extension {
  if ("kamon".equalsIgnoreCase(system.name)) {

    val enabled = system.settings.config getBoolean "dashboard.enabled"
    val interface = system.settings.config getString "dashboard.interface"
    val port = system.settings.config getInt "dashboard.port"

    if (enabled) {
      val service = system.actorOf(Props[DashboardServiceActor], "kamon-dashboard-service")
      IO(Http)(system) ! Http.Bind(service, interface, port)
    }
  }
}