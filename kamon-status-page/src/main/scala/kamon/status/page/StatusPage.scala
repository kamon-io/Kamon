package kamon
package status
package page

import com.typesafe.config.Config
import kamon.module.Module
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

/**
  * Uses an embedded web server to publish a simple status page with information about Kamon's internal status.
  */
class StatusPage extends Module {
  private val _logger = LoggerFactory.getLogger(classOf[StatusPage])
  @volatile private var _statusPageServer: Option[StatusPageServer] = None

  override def start(): Unit =
    init(Kamon.config())

  override def stop(): Unit =
    stopServer()

  override def reconfigure(newConfig: Config): Unit =
    init(newConfig)


  private def init(config: Config): Unit = synchronized {
    val listenConfig = config.getConfig("kamon.status-page.listen")
    val hostname = listenConfig.getString("hostname")
    val port = listenConfig.getInt("port")

    _statusPageServer.fold {
      // Starting a new server on the configured hostname/port
      startServer(hostname, port, Kamon.classLoader())

    }(existentServer => {
      // If the configuration has changed we will stop the previous version
      // and start a new one with the new hostname/port.

      if(existentServer.getHostname != hostname || existentServer.getListeningPort != port) {
        stopServer()
        startServer(hostname, port, Kamon.classLoader())
      }
    })
  }

  private def startServer(hostname: String, port: Int, resourceLoader: ClassLoader): Unit = {
    Try {
      val server = new StatusPageServer(hostname, port, resourceLoader, Kamon.status())
      server.start()
      server

    } match {
      case Success(server) =>
        _logger.info(s"Status page started on http://$hostname:$port/")
        _statusPageServer = Some(server)

      case Failure(t) =>
        _logger.error("Failed to start the status page embedded server", t)
    }
  }

  private def stopServer(): Unit = {
    _statusPageServer.foreach(_.stop())
    _statusPageServer = None
  }

}
