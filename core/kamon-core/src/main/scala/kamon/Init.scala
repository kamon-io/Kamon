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
import kamon.status.InstrumentationStatus
import org.slf4j.LoggerFactory

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor}
import scala.concurrent.Future

/**
  * Provides APIs for handling common initialization tasks like starting modules, attaching instrumentation and
  * reconfiguring Kamon.
  */
trait Init { self: Modules with Configuration with CurrentStatus with Metrics with Tracing =>
  private val _logger = LoggerFactory.getLogger(classOf[Init])
  @volatile private var _scheduler: Option[ScheduledExecutorService] = None

  self.onReconfigure(newConfig => reconfigureInit(newConfig))

  /**
    * Attempts to attach the instrumentation agent and start all registered modules.
    */
  def init(): Unit = {
    self.attachInstrumentation()
    self.initScheduler()
    self.loadModules()
    self.moduleRegistry().init()
  }

  /**
    * Reconfigures Kamon to use the provided configuration and then attempts to attach the instrumentation agent and
    * start all registered modules.
    */
  def init(config: Config): Unit = {
    self.attachInstrumentation()
    self.initScheduler()
    self.reconfigure(config)
    self.loadModules()
    self.moduleRegistry().init()

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
    if(!InstrumentationStatus.create(warnIfFailed = false).present) {
      try {
        val attacherClass = Class.forName("kamon.bundle.Bundle")
        val attachMethod = attacherClass.getDeclaredMethod("attach")
        attachMethod.invoke(null)
      } catch {
        case _: ClassNotFoundException =>
          _logger.warn("Failed to attach the instrumentation because the Kamon Bundle is not present on the classpath")

        case t: Throwable =>
          _logger.error("Failed to attach the instrumentation agent", t)
      }
    }
  }

  private def initScheduler(): Unit = {
    val newScheduler = newScheduledThreadPool(2, numberedThreadFactory("kamon-scheduler", daemon = true))
    self.tracer().bindScheduler(newScheduler)
    self.registry().bindScheduler(newScheduler)
  }

  private def stopScheduler(): Unit = {
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
