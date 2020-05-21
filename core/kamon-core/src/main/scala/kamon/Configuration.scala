/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

/**
  * Exposes APIs to access and modify Kamon's configuration and to get notified of reconfigure events.
  */
trait Configuration {
  private val logger = LoggerFactory.getLogger(classOf[Configuration])
  private var _currentConfig: Config = loadInitialConfiguration()
  private var _onReconfigureHooks = Seq.empty[Configuration.OnReconfigureHook]
  
  def enabled: Boolean = _enabled
  @volatile private var _enabled = _currentConfig.getBoolean("kamon.enabled")


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
    _enabled = newConfig.getBoolean("kamon.enabled")
    _onReconfigureHooks.foreach(hook => {
      try {
        hook.onReconfigure(newConfig)
      } catch {
        case error: Throwable => logger.error("Exception occurred while trying to run a OnReconfigureHook", error)
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
    try {
      ConfigFactory.load(ClassLoading.classLoader())
    } catch {
      case t: Throwable =>
        logger.warn("Failed to load the default configuration, attempting to load the reference configuration", t)

        try {
          val referenceConfig = ConfigFactory.defaultReference(ClassLoading.classLoader())
          logger.warn("Initializing with the default reference configuration, none of the user settings might be in effect", t)
          referenceConfig
        } catch {
          case t: Throwable =>
            logger.error("Failed to load the reference configuration, please check your reference.conf files for errors", t)
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
