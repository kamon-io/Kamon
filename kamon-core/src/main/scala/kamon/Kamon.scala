/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package kamon

import _root_.akka.actor
import _root_.akka.actor._
import com.typesafe.config.Config
import kamon.metric._
import kamon.supervisor.ModuleSupervisor
import kamon.trace.{ Tracer, TracerExtension }

class Kamon(val actorSystem: ActorSystem) {
  val metrics: MetricsExtension = Metrics.get(actorSystem)
  val tracer: TracerExtension = Tracer.get(actorSystem)
  val userMetrics: UserMetricsExtension = UserMetrics.get(actorSystem)

  // This will cause all auto-start modules to initiate.
  ModuleSupervisor.get(actorSystem)

  def shutdown(): Unit =
    actorSystem.shutdown()
}

object Kamon {
  trait Extension extends actor.Extension
  def apply[T <: Extension](key: ExtensionId[T])(implicit system: ActorSystem): T = key(system)

  def apply(): Kamon =
    apply("kamon")

  def apply(actorSystemName: String): Kamon =
    apply(ActorSystem(actorSystemName))

  def apply(actorSystemName: String, config: Config): Kamon =
    apply(ActorSystem(actorSystemName, config))

  def apply(system: ActorSystem): Kamon =
    new Kamon(system)

  def create(): Kamon =
    apply()

  def create(actorSystemName: String): Kamon =
    apply(ActorSystem(actorSystemName))

  def create(actorSystemName: String, config: Config): Kamon =
    apply(ActorSystem(actorSystemName, config))

  def create(system: ActorSystem): Kamon =
    new Kamon(system)

}