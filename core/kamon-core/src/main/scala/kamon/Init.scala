/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon

import com.typesafe.config.Config
import kamon.module.Module
import kamon.status.{BuildInfo, InstrumentationStatus}
import org.slf4j.LoggerFactory

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor}
import scala.concurrent.Future

/**
  * Provides APIs for handling common initialization tasks like starting modules, attaching instrumentation and
  * reconfiguring Kamon.
  */
trait Init {
  self: ModuleManagement with Configuration with CurrentStatus with Metrics with Utilities with Tracing
    with ContextStorage =>
  private val _logger = LoggerFactory.getLogger(classOf[Init])
  @volatile private var _scheduler: Option[ScheduledExecutorService] = None

  self.onReconfigure(newConfig => reconfigureInit(newConfig))

  /**
    * Attempts to attach the instrumentation agent and start all registered modules.
    */
  def init(): Unit = {
    if (enabled()) {
      if (shouldAttachInstrumentation()) {
        self.attachInstrumentation()
      }

      self.initScheduler()
      self.loadModules()
      self.moduleRegistry().init()
    } else {
      self.disableInstrumentation()
    }

    self.logInitStatusInfo()
  }

  /**
    * Reconfigures Kamon to use the provided configuration and then attempts to attach the instrumentation agent and
    * start all registered modules.
    */
  def init(config: Config): Unit = {
    self.reconfigure(config)

    if (enabled()) {
      if (shouldAttachInstrumentation()) {
        self.attachInstrumentation()
      }

      self.initScheduler()
      self.loadModules()
      self.moduleRegistry().init()
    } else {
      self.disableInstrumentation()
    }

    self.logInitStatusInfo()

  }

  /**
    * Initializes Kamon without trying to attach the instrumentation agent from the Kamon Bundle.
    */
  def initWithoutAttaching(): Unit = {
    if (enabled()) {
      self.initScheduler()
      self.loadModules()
      self.moduleRegistry().init()
    } else {
      self.disableInstrumentation()
    }

    self.logInitStatusInfo()
  }

  /**
    * Initializes Kamon without trying to attach the instrumentation agent from the Kamon Bundle.
    */
  def initWithoutAttaching(config: Config): Unit = {
    self.reconfigure(config)

    if (enabled()) {
      self.initWithoutAttaching()
    } else {
      self.disableInstrumentation()
      self.logInitStatusInfo()
    }
  }

  def stop(): Future[Unit] = {
    self.clearRegistry()
    self.stopScheduler()
    self.moduleRegistry().shutdown()
    self.stopModules()
  }

  /**
    * Tries to attach the Kanela instrumentation agent, if the Kamon Bundle dependency is available on the classpath. If
    * the Status module indicates that instrumentation has been already applied this method will not try to do anything.
    */
  def attachInstrumentation(): Unit = {
    if (!InstrumentationStatus.create(warnIfFailed = false).present) {
      try {
        val attacherClass = Class.forName("kamon.runtime.Attacher")
        val attachMethod = attacherClass.getDeclaredMethod("attach")
        attachMethod.invoke(null)
      } catch {
        case _: ClassNotFoundException =>
          _logger.warn(
            "Your application is running without the Kanela instrumentation agent. None of Kamon's automatic " +
            "instrumentation will be applied to the current JVM. Consider using the kamon-bundle dependency " +
            "or setting up the Kanela agent via the -javaagent:/path/to/kanela.jar command-line option"
          )

        case t: Throwable =>
          _logger.error("Failed to attach the Kanela agent included in the kamon-bundle", t)
      }
    }
  }

  private def logInitStatusInfo(): Unit = {
    def bold(text: String) = s"\u001b[1m${text}\u001b[0m"
    def red(text: String) = bold(s"\u001b[31m${text}\u001b[0m")
    def green(text: String) = bold(s"\u001b[32m${text}\u001b[0m")

    val isEnabled = enabled()
    val showBanner = !config().getBoolean("kamon.init.hide-banner")

    if (isEnabled) {
      val instrumentationStatus = status().instrumentation()
      val kanelaVersion = instrumentationStatus.kanelaVersion
        .map(v => green("v" + v))
        .getOrElse(red("not found"))

      if (showBanner) {
        _logger.info(
          s"""
             | _
             || |
             || | ____ _ _ __ ___   ___  _ __
             || |/ / _  |  _ ` _ \\ / _ \\|  _ \\
             ||   < (_| | | | | | | (_) | | | |
             ||_|\\_\\__,_|_| |_| |_|\\___/|_| |_|
             |=====================================
             |Initializing Kamon Telemetry ${green("v" + BuildInfo.version)} / Kanela ${kanelaVersion}
             |""".stripMargin
        )
      } else
        _logger.info(s"Initializing Kamon Telemetry v${BuildInfo.version} / Kanela ${kanelaVersion}")
    } else {
      _logger.warn(
        s"Kamon is ${red("DISABLED")}. No instrumentation, reporters, or context propagation will be applied on this " +
        "process. Restart the process with kamon.enabled=yes to restore Kamon's functionality"
      )
    }

  }

  /**
    * Tries to disable the Kanela agent, in case it was attached via the -javaagent:... option. The agent is always
    * attached to the System Classloader so we try to find it there and call "disable" on it.
    */
  private def disableInstrumentation(): Unit = {
    try {
      Class.forName("kanela.agent.Kanela", true, ClassLoader.getSystemClassLoader)
        .getDeclaredMethod("disable")
        .invoke(null)

      _logger.info("Disabled the Kanela instrumentation agent. Classes will not be instrumented in this process")

    } catch {
      case _: ClassNotFoundException =>
      // Do nothing. This means that Kanela wasn't loaded so there was no need to do anything.

      case _: NoSuchMethodException =>
        _logger.error("Failed to disable the Kanela instrumentation agent. Please ensure you are using Kanela >=1.0.17")

      case t: Throwable =>
        _logger.error("Failed to disable the Kanela instrumentation agent", t)
    }
  }

  private def initScheduler(): Unit = synchronized {
    val newScheduler = newScheduledThreadPool(2, numberedThreadFactory("kamon-scheduler", daemon = true))
    self.tracer().bindScheduler(newScheduler)
    self.registry().bindScheduler(newScheduler)
  }

  private def stopScheduler(): Unit = synchronized {
    self.tracer().shutdown()
    self.registry().shutdown()
    _scheduler.foreach(_.shutdown())
    _scheduler = None
  }

  private def reconfigureInit(config: Config): Unit = {
    _scheduler.foreach {
      case stpe: ScheduledThreadPoolExecutor =>
        val newPoolSize = config.getInt("kamon.scheduler-pool-size")
        stpe.setCorePoolSize(newPoolSize)

      case _ => // cannot change the pool size on other unknown types.
    }
  }
}
