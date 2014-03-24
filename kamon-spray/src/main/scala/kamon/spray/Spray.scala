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
import spray.http.HttpRequest

object Spray extends ExtensionId[SprayExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Spray
  def createExtension(system: ExtendedActorSystem): SprayExtension = new SprayExtension(system)

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

  val clientSegmentCollectionStrategy: ClientSegmentCollectionStrategy.Strategy =
    config.getString("client.segment-collection-strategy") match {
      case "pipelining" ⇒ ClientSegmentCollectionStrategy.Pipelining
      case "internal"   ⇒ ClientSegmentCollectionStrategy.Internal
      case other ⇒ throw new IllegalArgumentException(s"Configured segment-collection-strategy [$other] is invalid, " +
        s"only pipelining and internal are valid options.")
    }

  // Later we should expose a way for the user to customize this.
  def assignHttpClientRequestName(request: HttpRequest): String = request.uri.authority.host.address
}
