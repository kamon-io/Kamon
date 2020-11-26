/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

/**
  * Provides APIs for handling common initialization tasks like starting modules, attaching instrumentation and
  * reconfiguring Kamon.
  */
trait Init { self: ModuleLoading with Configuration with CurrentStatus =>
  private val _logger = LoggerFactory.getLogger(classOf[Init])

  /**
    * Attempts to attach the instrumentation agent and start all registered modules.
    */
  def init(): Unit = {
    self.attachInstrumentation()
    self.loadModules()
  }

  /**
    * Reconfigures Kamon to use the provided configuration and then attempts to attach the instrumentation agent and
    * start all registered modules.
    */
  def init(config: Config): Unit = {
    self.attachInstrumentation()
    self.reconfigure(config)
    self.loadModules()
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
}
