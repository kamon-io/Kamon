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
import _root_.akka.event.Logging
import com.typesafe.config.{ ConfigFactory, Config }
import kamon.metric._
import kamon.trace.{ TracerModuleImpl, TracerModule }

object Kamon {
  trait Extension extends actor.Extension

  private case class KamonCoreComponents(metrics: MetricsModule, tracer: TracerModule)

  private val shouldAutoStart = isSystemPropertyEnabled("kamon.auto-start")
  private val shouldWarnOnDuplicateStart = !isSystemPropertyEnabled("kamon.disable-duplicate-start-warning")

  @volatile private var _system: ActorSystem = _
  @volatile private var _coreComponents: Option[KamonCoreComponents] = None

  def start(config: Config): Unit = synchronized {

    def resolveInternalConfig: Config = {
      val internalConfig = config.getConfig("kamon.internal-config")

      config
        .withoutPath("akka")
        .withoutPath("spray")
        .withFallback(internalConfig)
    }

    if (_coreComponents.isEmpty) {
      val metrics = MetricsModuleImpl(config)
      val tracer = TracerModuleImpl(metrics, config)

      _coreComponents = Some(KamonCoreComponents(metrics, tracer))
      _system = ActorSystem("kamon", resolveInternalConfig)

      metrics.start(_system)
      tracer.start(_system)
      _system.registerExtension(ModuleLoader)

    } else if (shouldWarnOnDuplicateStart) {
      val logger = Logging(_system, "Kamon")
      logger.warning("An attempt to start Kamon has been made, but Kamon has already been started.")
    }
  }

  private def isSystemPropertyEnabled(propertyName: String): Boolean =
    sys.props.get(propertyName).map(_.equals("true")).getOrElse(false)

  def start(): Unit =
    start(ConfigFactory.load)

  def shutdown(): Unit = {
    _coreComponents = None
    _system.shutdown()
    _system = null
  }

  def metrics: MetricsModule =
    ifStarted(_.metrics)

  def tracer: TracerModule =
    ifStarted(_.tracer)

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
    _coreComponents.map(thunk(_)) getOrElse {
      if (shouldAutoStart) {
        start()
        thunk(_coreComponents.get)

      } else sys.error("Kamon has not been started yet. You must either explicitlt call Kamon.start(...) or enable " +
        "automatic startup by adding -Dkamon.auto-start=true to your JVM options.")
    }

}

