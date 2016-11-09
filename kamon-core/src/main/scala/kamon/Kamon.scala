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
import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions, ConfigResolveOptions }
import kamon.metric._
import kamon.trace.TracerModuleImpl
import kamon.util.logger.LazyLogger

import _root_.scala.util.control.NonFatal
import _root_.scala.util.{ Failure, Success, Try }

trait ConfigProvider {
  def config: Config

  final def patchedConfig: Config = {
    val internalConfig = config.getConfig("kamon.internal-config")
    config
      .withoutPath("akka")
      .withoutPath("spray")
      .withFallback(internalConfig)
  }
}

object Kamon {

  private val log = LazyLogger("Kamon")

  trait Extension extends actor.Extension

  def defaultConfig = ConfigFactory.load(this.getClass.getClassLoader, ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))

  class KamonDefaultConfigProvider extends ConfigProvider {
    def config = resolveConfiguration

    private def resolveConfiguration: Config = {
      val defaultConf = defaultConfig

      defaultConf.getString("kamon.config-provider") match {
        case "default" ⇒ defaultConf
        case fqcn ⇒
          val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)
          dynamic.createInstanceFor[ConfigProvider](fqcn, Nil).get.config
      }
    }
  }

  class KamonConfigProvider(_config: Config) extends ConfigProvider {
    def config = _config
  }

  private[kamon] var configProvider: Option[ConfigProvider] = None
  def config: Config =
    configProvider match {
      case Some(provider) ⇒ provider.config
      case None           ⇒ throw new Exception("Kamon.start() not called yet")
    }
  lazy val metrics = MetricsModuleImpl(config)
  lazy val tracer = TracerModuleImpl(metrics, config)

  private lazy val _system = {
    val patchedConfig =
      configProvider match {
        case Some(provider) ⇒ provider.patchedConfig
        case None ⇒
          throw new Exception("Kamon.start() not called yet")
      }

    log.info("Initializing Kamon...")

    tryLoadAutoweaveModule()

    ActorSystem("kamon", patchedConfig)
  }

  private lazy val _start = {
    metrics.start(_system)
    tracer.start(_system)
    _system.registerExtension(ModuleLoader)
  }

  def start(): Unit = {
    configProvider = Some(new KamonDefaultConfigProvider())
    _start
  }

  def start(conf: Config): Unit = {
    configProvider = Some(new KamonConfigProvider(conf))
    _start
  }

  def start(provider: ConfigProvider): Unit = {
    configProvider = Some(provider)
    _start
  }

  def shutdown(): Unit = {
    _system.shutdown()
  }

  private def tryLoadAutoweaveModule(): Unit = {
    Try {
      val autoweave = Class.forName("kamon.autoweave.Autoweave")
      autoweave.getDeclaredMethod("attach").invoke(autoweave.newInstance())
    } match {
      case Success(_) ⇒
        val color = (msg: String) ⇒ s"""\u001B[32m${msg}\u001B[0m"""
        log.info(color("Kamon-autoweave has been successfully loaded."))
        log.info(color("The AspectJ loadtime weaving agent is now attached to the JVM (you don't need to use -javaagent)."))
        log.info(color("This offers extra flexibility but obviously any classes loaded before attachment will not be woven."))
      case Failure(NonFatal(reason)) ⇒ log.debug(s"Kamon-autoweave failed to load. Reason: ${reason.getMessage}.")
    }
  }
}
