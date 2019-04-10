package kamon

import scala.util.Try
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

/**
  * Exposes APIs to access and modify Kamon's configuration and to get notified of reconfigure events.
  */
trait Configuration { self: ClassLoading =>
  private val logger = LoggerFactory.getLogger(classOf[Configuration])
  private var _currentConfig: Config = ConfigFactory.load(self.classLoader())
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
      Try(hook.onReconfigure(newConfig)).failed.foreach(error =>
        logger.error("Exception occurred while trying to run a OnReconfigureHook", error)
      )
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

}

object Configuration {

  trait OnReconfigureHook {
    def onReconfigure(newConfig: Config): Unit
  }

}
