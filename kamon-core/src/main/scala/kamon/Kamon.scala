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
import com.typesafe.config.{ ConfigFactory, Config }
import kamon.metric._
import kamon.trace.{ TracerExtensionImpl, TracerExtension }

object Kamon {
  trait Extension extends actor.Extension

  private case class KamonCoreComponents(
    metrics: MetricsExtension,
    tracer: TracerExtension,
    userMetrics: UserMetricsExtension)

  @volatile private var _system: ActorSystem = _
  @volatile private var _coreComponents: Option[KamonCoreComponents] = None

  def start(config: Config): Unit = synchronized {
    if (_coreComponents.isEmpty) {
      val metrics = MetricsExtensionImpl(config)
      val simpleMetrics = UserMetricsExtensionImpl(metrics)
      val tracer = TracerExtensionImpl(metrics, config)

      _coreComponents = Some(KamonCoreComponents(metrics, tracer, simpleMetrics))
      _system = ActorSystem("kamon", config)

      metrics.start(_system)
      tracer.start(_system)

    } else sys.error("Kamon has already been started.")
  }

  def start(): Unit =
    start(ConfigFactory.load)

  def metrics: MetricsExtension =
    ifStarted(_.metrics)

  def tracer: TracerExtension =
    ifStarted(_.tracer)

  def userMetrics: UserMetricsExtension =
    ifStarted(_.userMetrics)

  def apply[T <: Kamon.Extension](key: ExtensionId[T]): T =
    ifStarted { _ ⇒
      if (_system ne null)
        key(_system)
      else
        sys.error("Cannot retrieve extensions while Kamon is being initialized.")
    }

  def extension[T <: Kamon.Extension](key: ExtensionId[T]): T =
    apply(key)

  private def ifStarted[T](thunk: KamonCoreComponents ⇒ T): T =
    _coreComponents.map(thunk(_)) getOrElse (sys.error("Kamon has not been started yet."))

}

