/*
 * =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.okhttp3.utils

import java.net.InetSocketAddress
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.{Server, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.slf4j.LoggerFactory

import scala.collection.immutable.TreeMap
import scala.collection.mutable

/**
  * Runs a Servlet or a Filter on an embedded Jetty server.
  */
private class JettyServer(socketAddress: InetSocketAddress = new InetSocketAddress(0)) {
  val server = new Server(socketAddress)
  val context = new ServletContextHandler(server, "/")
  val _requests: mutable.ListBuffer[HttpServletRequest] = mutable.ListBuffer()

  def start(servlet: Servlet, path: String = "/*"): this.type = {
    context.addServlet(new ServletHolder(servlet), "/")
    server.start()
    this
  }

  def stop(): this.type = {
    server.stop()
    this
  }

  def join(): this.type = {
    server.join()
    this
  }

  def selectedPort: Int = {
    server.getConnectors()(0).asInstanceOf[ServerConnector].getLocalPort
  }

  def host: String = {
    server.getConnectors()(0).asInstanceOf[ServerConnector].getHost
  }

  def requests: List[HttpServletRequest] = _requests.toList
}

case class RequestHolder(uri: String, headers: Map[String, String]) {
  def header(name: String): Option[String] = headers.get(name)
}

trait ServletTestSupport extends Servlet {

  import collection.JavaConverters._

  private val _requests: BlockingQueue[RequestHolder] = new LinkedBlockingQueue[RequestHolder]()

  def requests: List[RequestHolder] = {
    val result: java.util.List[RequestHolder] = new java.util.ArrayList[RequestHolder]
    _requests.drainTo(result)
    result.asScala.toList
  }

  def addRequest(req: HttpServletRequest): Unit = {
    _requests.offer(RequestHolder(req.getRequestURI, headers(req)))
  }

  private def headers(req: HttpServletRequest): Map[String, String] = {
    val headersIterator = req.getHeaderNames
    val headers = Map.newBuilder[String, String]
    while (headersIterator.hasMoreElements) {
      val name = headersIterator.nextElement()
      headers += (name -> req.getHeader(name))
    }
    TreeMap(headers.result().toList: _*)(Ordering.comparatorToOrdering(String.CASE_INSENSITIVE_ORDER))
  }
}

trait JettySupport {

  private val logger = LoggerFactory.getLogger(classOf[JettySupport])

  val servlet: ServletTestSupport

  private var jetty = Option.empty[JettyServer]

  def startServer(): Unit = {
    jetty = Some(new JettyServer().start(servlet))
    logger.info(s"Jetty started at ${host}:${port}")
  }

  def stopServer(): Unit = {
    jetty.foreach(_.stop())
  }

  def host: String = jetty.get.host

  def port: Int = jetty.get.selectedPort

  def consumeSentRequests(): List[RequestHolder] = servlet.requests
}
