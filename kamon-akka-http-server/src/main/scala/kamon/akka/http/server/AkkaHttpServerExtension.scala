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
package kamon.akka.http.server

import akka.actor._
import akka.event._
import akka.http.scaladsl.model.HttpRequest
import kamon.Kamon
import kamon.metric.Entity

object AkkaHttpServer extends ExtensionId[AkkaHttpServerExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: Extension] = AkkaHttpServer
  def createExtension(system: ExtendedActorSystem): AkkaHttpServerExtension = new AkkaHttpServerExtensionImpl(system)
}

trait AkkaHttpServerExtension extends Kamon.Extension {
  def settings: AkkaHttpServerExtensionSettings
  def log: LoggingAdapter
  def serverMetrics: AkkaHttpServerMetrics
  def generateTraceName(request: HttpRequest): String
}

class AkkaHttpServerExtensionImpl(system: ExtendedActorSystem) extends AkkaHttpServerExtension {
  val settings: AkkaHttpServerExtensionSettings =
    AkkaHttpServerExtensionSettings(system)

  val log: LoggingAdapter =
    Logging(system, "AkkaHttpServerExtension")

  def serverMetrics: AkkaHttpServerMetrics = {
    val entity = Entity("akka-http-server", AkkaHttpServerMetrics.category)
    Kamon.metrics.entity(AkkaHttpServerMetrics, entity)
  }

  def generateTraceName(request: HttpRequest): String =
    settings.nameGenerator.generateTraceName(request)

}

trait NameGenerator {
  def generateTraceName(request: HttpRequest): String
}

class DefaultNameGenerator extends NameGenerator {
  def generateTraceName(request: HttpRequest): String =
    "UnnamedTrace"
}
