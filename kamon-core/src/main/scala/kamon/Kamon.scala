/* =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import kamon.metric._
import kamon.trace.TracerModuleImpl
import kamon.util.logger.LazyLogger

import _root_.scala.util.control.NonFatal
import _root_.scala.util.{Failure, Success, Try}

object Kamon {
  trait Extension extends actor.Extension

  @volatile private var kamonInstance = new Instance()

  def config = kamonInstance.config
  def metrics = kamonInstance.metrics
  def tracer = kamonInstance.tracer

  def start(): Unit = synchronized {
    kamonInstance.start()
  }

  def start(conf: Config): Unit = synchronized {
    kamonInstance.start(conf)
  }

  def shutdown(): Unit = synchronized {
    kamonInstance.shutdown()
    kamonInstance = new Instance()
  }

  private class Instance() {
    private val log = LazyLogger(classOf[Instance])
    private var actorSystem: ActorSystem = _

    var started = false
    var config: Config = defaultConfig
    val metrics = MetricsModuleImpl(config)
    val tracer = TracerModuleImpl(metrics, config)

    private lazy val _start = {
      log.info("Initializing Kamon...")
      tryLoadAutoweaveModule()
      actorSystem = ActorSystem("kamon", config)
      metrics.start(actorSystem, config)
      tracer.start(actorSystem, config)
      actorSystem.registerExtension(ModuleLoader)
      started = true
    }

    def start(): Unit = {
      _start
    }

    def start(conf: Config): Unit = {
      config = patchConfiguration(conf)
      _start
    }

    def shutdown(): Unit = {
      if (started) {
        actorSystem.terminate()
      }
    }

    private def defaultConfig = {
      patchConfiguration(
        ConfigFactory.load(
          this.getClass.getClassLoader,
          ConfigParseOptions.defaults(),
          ConfigResolveOptions.defaults().setAllowUnresolved(true)
        )
      )
    }

    private def patchConfiguration(config: Config): Config = {
      val internalConfig = config.getConfig("kamon.internal-config")
      config
        .withoutPath("akka")
        .withoutPath("spray")
        .withFallback(internalConfig)
    }

    private def tryLoadAutoweaveModule(): Unit = {
      Try {
        val autoweave = Class.forName("kamon.autoweave.Autoweave")
        autoweave.getDeclaredMethod("attach").invoke(autoweave.newInstance())
      } match {
        case Success(_) ⇒
          val color = (msg: String) ⇒ s"""\u001B[32m${msg}\u001B[0m"""
          log.info(color("Kamon-autoweave has been successfully loaded."))
          log.info(color("The AspectJ load time weaving agent is now attached to the JVM (you don't need to use -javaagent)."))
          log.info(color("This offers extra flexibility but obviously any classes loaded before attachment will not be woven."))
        case Failure(NonFatal(reason)) ⇒ log.debug(s"Kamon-autoweave failed to load. Reason: ${reason.getMessage}.")
      }
    }
  }
}
