/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.spray

import akka.actor.{ ExtendedActorSystem, ExtensionIdProvider, ExtensionId }
import akka.actor
import akka.event.{ Logging, LoggingAdapter }
import kamon.Kamon
import kamon.util.http.HttpServerMetrics
import kamon.metric.Entity
import org.slf4j.LoggerFactory
import spray.http.HttpHeaders.Host
import spray.http.HttpRequest

object SprayExtension {
  val log = LoggerFactory.getLogger("kamon.spray.SprayExtension")
  val settings = SprayExtensionSettings(Kamon.config)
  val SegmentLibraryName = "spray-client"

  val httpServerMetrics = {
    val entity = Entity("spray-server", HttpServerMetrics.category)
    Kamon.metrics.entity(HttpServerMetrics, entity)
  }

  def generateTraceName(request: HttpRequest): String =
    settings.nameGenerator.generateTraceName(request)

  def generateRequestLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateRequestLevelApiSegmentName(request)

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateHostLevelApiSegmentName(request)
}

trait NameGenerator {
  def generateTraceName(request: HttpRequest): String
  def generateRequestLevelApiSegmentName(request: HttpRequest): String
  def generateHostLevelApiSegmentName(request: HttpRequest): String
}

class DefaultNameGenerator extends NameGenerator {

  def generateRequestLevelApiSegmentName(request: HttpRequest): String = {
    val uriAddress = request.uri.authority.host.address
    if (uriAddress.equals("")) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
  }

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    hostFromHeaders(request).getOrElse("unknown-host")

  def generateTraceName(request: HttpRequest): String =
    "UnnamedTrace"

  private def hostFromHeaders(request: HttpRequest): Option[String] =
    request.header[Host].map(_.host)

}
