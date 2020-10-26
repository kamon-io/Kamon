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

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

/**
  * Exposes APIs to access and modify Kamon's configuration and to get notified of reconfigure events.
  */
trait Configuration {
  private val _logger = LoggerFactory.getLogger(classOf[Configuration])
  private var _currentConfig: Config = loadInitialConfiguration()
  private var _onReconfigureHooks = Seq.empty[Configuration.OnReconfigureHook]


  /**
    * Retrieve Kamon's current configuration.
    */
  def config(): Config =
    _currentConfig

  /**
    * Supply a new Config instance to rule Kamon's world.
    */
  def reconfigure(newConfig: Config): Unit = synchronized {
    _currentConfig = newConfig
    _onReconfigureHooks.foreach(hook => {
      try {
        hook.onReconfigure(newConfig)
      } catch {
        case NonFatal(t) => _logger.error("Exception occurred while trying to run a OnReconfigureHook", t)
      }
    })
  }

  /**
    * Register a reconfigure hook that will be run when the a call to Kamon.reconfigure(config) is performed. All
    * registered hooks will run sequentially in the same Thread that calls Kamon.reconfigure(config).
    */
  def onReconfigure(hook: Configuration.OnReconfigureHook): Unit = synchronized {
    _onReconfigureHooks = hook +: _onReconfigureHooks
  }

  /**
    * Register a reconfigure hook that will be run when the a call to Kamon.reconfigure(config) is performed. All
    * registered hooks will run sequentially in the same Thread that calls Kamon.reconfigure(config).
    */
  def onReconfigure(hook: (Config) => Unit): Unit = {
    onReconfigure(new Configuration.OnReconfigureHook {
      override def onReconfigure(newConfig: Config): Unit = hook.apply(newConfig)
    })
  }


  private def loadInitialConfiguration(): Config = {
    import System.{err, lineSeparator}

    try {
      ConfigFactory.load(ClassLoading.classLoader())
    } catch {
      case NonFatal(t) =>
        err.println("Kamon couldn't load configuration settings from your *.conf files due to: " +
          t.getMessage + " at " + t.getStackTrace().mkString("", lineSeparator, lineSeparator))

        try {
          val referenceConfig = ConfigFactory.defaultReference(ClassLoading.classLoader())
          err.println("Initializing Kamon with the reference configuration, none of the user settings will be in effect")
          referenceConfig
        } catch {
          case NonFatal(t) =>
            err.println("Kamon couldn't load the reference configuration settings from the reference.conf files due to: " +
              t.getMessage + " at " + t.getStackTrace().mkString("", lineSeparator, lineSeparator))

            ConfigFactory.empty()
        }
    }
  }

}

object Configuration {

  trait OnReconfigureHook {
    def onReconfigure(newConfig: Config): Unit
  }

}
