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

package kamon.metrics

import akka.actor.{ Props, ExtendedActorSystem, ExtensionIdProvider, ExtensionId }
import akka.actor
import kamon.Kamon

object ActorMetrics extends ExtensionId[ActorMetricsExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = ActorMetrics
  def createExtension(system: ExtendedActorSystem): ActorMetricsExtension = new ActorMetricsExtension(system)

}

class ActorMetricsExtension(val system: ExtendedActorSystem) extends Kamon.Extension with ActorMetricsOps {
  lazy val metricsDispatcher = system.actorOf(Props[ActorMetricsDispatcher], "kamon-actor-metrics")
}
