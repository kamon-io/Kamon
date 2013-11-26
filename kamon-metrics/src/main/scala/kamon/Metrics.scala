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
package kamon

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorRef, ExtendedActorSystem, ExtensionIdProvider, ExtensionId }
import kamon.Kamon.Extension
import akka.actor

object Metrics extends ExtensionId[MetricsExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Metrics
  def createExtension(system: ExtendedActorSystem): MetricsExtension = new MetricsExtension(system)

}

class MetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  def manager: ActorRef = ???
}

