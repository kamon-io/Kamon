/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.metric._
import kamon.trace.TracerModuleImpl
import kamon.util.logger.LazyLogger

object Kamon {
  private val log = LazyLogger(getClass)

  trait Extension extends actor.Extension

  val config = resolveConfiguration
  val metrics = MetricsModuleImpl(config)
  val tracer = TracerModuleImpl(metrics, config)

  private lazy val _system = {
    val internalConfig = config.getConfig("kamon.internal-config")

    val patchedConfig = config
      .withoutPath("akka")
      .withoutPath("spray")
      .withFallback(internalConfig)

    log.info("Initializing Kamon...")

    ActorSystem("kamon", patchedConfig)
  }

  private lazy val _start = {
    metrics.start(_system)
    tracer.start(_system)
    _system.registerExtension(ModuleLoader)
  }

  def start(): Unit = _start

  def shutdown(): Unit = {
    _system.terminate()
  }

  private def resolveConfiguration: Config = {
    val defaultConfig = ConfigFactory.load()

    defaultConfig.getString("kamon.config-provider") match {
      case "default" ⇒ defaultConfig
      case fqcn ⇒
        val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)
        dynamic.createInstanceFor[ConfigProvider](fqcn, Nil).get.config
    }
  }
}

trait ConfigProvider {
  def config: Config
}

