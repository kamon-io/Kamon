package kamon

import com.typesafe.config.Config
import kamon.status.{Status, StatusPageServer}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

trait StatusPage { self: Configuration with ClassLoading with ModuleLoading with Metrics with Configuration =>
  private val _log = LoggerFactory.getLogger(classOf[StatusPage])
  @volatile private var _statusPageServer: Option[StatusPageServer] = None
  private val _status = new Status(self._moduleRegistry, self._metricsRegistry, self)

  // Initial configuration and reconfigures
  init(self.config())
  self.onReconfigure(newConfig => self.init(newConfig))


  /**
    * Allows to access internal Kamon status for troubleshooting and displaying purposes. All information returned
    * by the Status instance is a immutable snapshot of the current state of a given component.
    */
  def status(): Status =
    _status


  private def init(config: Config): Unit = synchronized {
    val isStatusPageEnabled = config.getBoolean("kamon.status.enabled")

    if(isStatusPageEnabled) {
      val hostname = config.getString("kamon.status.listen.hostname")
      val port = config.getInt("kamon.status.listen.port")

      _statusPageServer.fold {
        // Starting a new server on the configured hostname/port
        startServer(hostname, port, self.classLoader())

      }(existentServer => {
        // If the configuration has changed we will stop the previous version
        // and start a new one with the new hostname/port.

        if(existentServer.getHostname != hostname || existentServer.getListeningPort != port) {
          stopServer()
          startServer(hostname, port, self.classLoader())
        }
      })

    } else {
      _statusPageServer.foreach(_ => stopServer())
    }
  }

  private def startServer(hostname: String, port: Int, resourceLoader: ClassLoader): Unit = {
    Try {

      val server = new StatusPageServer(hostname, port, resourceLoader, _status)
      server.start()
      server

    } match {
      case Success(server) =>
        _log.info(s"Status page started on http://$hostname:$port/")
        _statusPageServer = Some(server)

      case Failure(t) =>
        _log.error("Failed to start the status page embedded server", t)
    }
  }

  private def stopServer(): Unit = {
    _statusPageServer.foreach(_.stop())
    _statusPageServer = None
  }

}
