package kamon

import com.typesafe.config.Config
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
    self.loadModules()
    self.attachInstrumentation()
  }

  /**
    * Reconfigures Kamon to use the provided configuration and then attempts to attach the instrumentation agent and
    * start all registered modules.
    */
  def init(config: Config): Unit = {
    self.reconfigure(config)
    self.loadModules()
    self.attachInstrumentation()
  }

  /**
    * Tries to attach the Kanela instrumentation agent, if available on the classpath. Users can get the Kanela agent
    * in the classpath by adding it explicitly or by using the kamon-bundle dependency. If the Status module indicates
    * that instrumentation has been already applied this method will not try to do anything.
    */
  def attachInstrumentation(): Unit = {
    if(!self.status().instrumentation().active) {
      try {
        val attacherClass = Class.forName("kanela.agent.attacher.Attacher")
        val attachMethod = attacherClass.getDeclaredMethod("attach")
        attachMethod.invoke(null)
      } catch {
        case _: ClassNotFoundException =>
          _logger.warn(
            "Failed to attach the instrumentation because the Kanela agent is not present on the classpath. " +
            "Consider adding the kamon-bundle or kanela-agent dependencies."
          )

        case t: Throwable =>
          _logger.error("Failed to attach the instrumentation agent", t)
      }
    }
  }
}
