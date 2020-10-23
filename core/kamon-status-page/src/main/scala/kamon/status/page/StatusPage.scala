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
package status
package page

import com.typesafe.config.Config
import kamon.module.{Module, ModuleFactory}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

/**
  * Uses an embedded web server to publish a simple status page with information about Kamon's internal status.
  */
class StatusPage(configPath: String) extends Module {
  private val _logger = LoggerFactory.getLogger(classOf[StatusPage])
  @volatile private var _statusPageServer: Option[StatusPageServer] = None
  init(Kamon.config().getConfig(configPath))

  def this() =
    this("kamon.status-page")

  override def stop(): Unit =
    stopServer()

  override def reconfigure(newConfig: Config): Unit =
    init(newConfig.getConfig(configPath))


  private def init(config: Config): Unit = synchronized {
    val hostname = config.getString("listen.hostname")
    val port = config.getInt("listen.port")

    _statusPageServer.fold {
      // Starting a new server on the configured hostname/port
      startServer(hostname, port, ClassLoading.classLoader())

    }(existentServer => {
      // If the configuration has changed we will stop the previous version
      // and start a new one with the new hostname/port.

      if(existentServer.getHostname != hostname || existentServer.getListeningPort != port) {
        stopServer()
        startServer(hostname, port, ClassLoading.classLoader())
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

object StatusPage {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new StatusPage()
  }
}
