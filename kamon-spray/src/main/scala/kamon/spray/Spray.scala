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
import kamon.Kamon
import kamon.http.HttpServerMetrics
import kamon.metric.Metrics
import spray.http.HttpHeaders.Host
import spray.http.HttpRequest

object Spray extends ExtensionId[SprayExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Spray
  def createExtension(system: ExtendedActorSystem): SprayExtension = new SprayExtension(system)

  val SegmentLibraryName = "spray-client"
}

object ClientSegmentCollectionStrategy {
  sealed trait Strategy
  case object Pipelining extends Strategy
  case object Internal extends Strategy
}

class SprayExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  private val config = system.settings.config.getConfig("kamon.spray")

  val includeTraceToken: Boolean = config.getBoolean("automatic-trace-token-propagation")
  val traceTokenHeaderName: String = config.getString("trace-token-header-name")
  val httpServerMetrics = Kamon(Metrics)(system).register(HttpServerMetrics, HttpServerMetrics.Factory).get
  // It's safe to assume that HttpServerMetrics will always exist because there is no particular filter for it.

  private val nameGeneratorFQN = config.getString("name-generator")
  private val nameGenerator: SprayNameGenerator = system.dynamicAccess.createInstanceFor[SprayNameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.

  val clientSegmentCollectionStrategy: ClientSegmentCollectionStrategy.Strategy =
    config.getString("client.segment-collection-strategy") match {
      case "pipelining" ⇒ ClientSegmentCollectionStrategy.Pipelining
      case "internal"   ⇒ ClientSegmentCollectionStrategy.Internal
      case other ⇒ throw new IllegalArgumentException(s"Configured segment-collection-strategy [$other] is invalid, " +
        s"only pipelining and internal are valid options.")
    }

  def generateTraceName(request: HttpRequest): String = nameGenerator.generateTraceName(request)
  def generateRequestLevelApiSegmentName(request: HttpRequest): String = nameGenerator.generateRequestLevelApiSegmentName(request)
  def generateHostLevelApiSegmentName(request: HttpRequest): String = nameGenerator.generateHostLevelApiSegmentName(request)
}

trait SprayNameGenerator {
  def generateTraceName(request: HttpRequest): String
  def generateRequestLevelApiSegmentName(request: HttpRequest): String
  def generateHostLevelApiSegmentName(request: HttpRequest): String
}

class DefaultSprayNameGenerator extends SprayNameGenerator {
  def hostFromHeaders(request: HttpRequest): Option[String] = request.header[Host].map(_.host)

  def generateRequestLevelApiSegmentName(request: HttpRequest): String = {
    val uriAddress = request.uri.authority.host.address
    if (uriAddress.equals("")) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
  }

  def generateHostLevelApiSegmentName(request: HttpRequest): String = hostFromHeaders(request).getOrElse("unknown-host")

  def generateTraceName(request: HttpRequest): String = request.method.value + ": " + request.uri.path
}
