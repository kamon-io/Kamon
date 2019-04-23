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
