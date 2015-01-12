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
import kamon.http.HttpServerMetrics
import kamon.metric.{ Entity, Metrics }
import spray.http.HttpHeaders.Host
import spray.http.HttpRequest

object Spray extends ExtensionId[SprayExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Spray
  def createExtension(system: ExtendedActorSystem): SprayExtension = new SprayExtensionImpl(system)

  val SegmentLibraryName = "spray-client"
}

trait SprayExtension extends Kamon.Extension {
  def settings: SprayExtensionSettings
  def log: LoggingAdapter
  def httpServerMetrics: HttpServerMetrics
  def generateTraceName(request: HttpRequest): String
  def generateRequestLevelApiSegmentName(request: HttpRequest): String
  def generateHostLevelApiSegmentName(request: HttpRequest): String
}

class SprayExtensionImpl(system: ExtendedActorSystem) extends SprayExtension {
  val settings = SprayExtensionSettings(system)
  val log = Logging(system, "SprayExtension")

  val httpServerMetrics = {
    val metricsExtension = Metrics.get(system)
    val factory = metricsExtension.instrumentFactory(HttpServerMetrics.category)
    val entity = Entity("spray-server", HttpServerMetrics.category)

    Metrics.get(system).register(entity, new HttpServerMetrics(factory)).recorder
  }

  def generateTraceName(request: HttpRequest): String =
    settings.nameGenerator.generateTraceName(request)

  def generateRequestLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateRequestLevelApiSegmentName(request)

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    settings.nameGenerator.generateHostLevelApiSegmentName(request)
}

trait SprayNameGenerator {
  def generateTraceName(request: HttpRequest): String
  def generateRequestLevelApiSegmentName(request: HttpRequest): String
  def generateHostLevelApiSegmentName(request: HttpRequest): String
}

class DefaultSprayNameGenerator extends SprayNameGenerator {

  def generateRequestLevelApiSegmentName(request: HttpRequest): String = {
    val uriAddress = request.uri.authority.host.address
    if (uriAddress.equals("")) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
  }

  def generateHostLevelApiSegmentName(request: HttpRequest): String =
    hostFromHeaders(request).getOrElse("unknown-host")

  def generateTraceName(request: HttpRequest): String =
    request.method.value + ": " + request.uri.path

  private def hostFromHeaders(request: HttpRequest): Option[String] =
    request.header[Host].map(_.host)

}
