/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.status.page

import java.io.InputStream
import java.util.Collections
import java.util.concurrent.{ExecutorService, Executors}

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.{Status => StatusCode}
import kamon.status.Status

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.compat.Platform.EOL

/**
  * Exposes an embedded HTTP server based on NanoHTTP.
  */
class StatusPageServer(hostname: String, port: Int, resourceLoader: ClassLoader, status: Status)
    extends NanoHTTPD(hostname, port) {

  private val RootResourceDirectory = "status-page"
  private val ResourceExtensionRegex = ".*\\.([a-zA-Z0-9]*)".r

  override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
    if (session.getMethod() == NanoHTTPD.Method.GET) {
      try {
        if (session.getUri().startsWith("/status")) {

          // Serve the current status data on Json.
          session.getUri() match {
            case "/status/settings"        => json(status.settings())
            case "/status/modules"         => json(status.moduleRegistry())
            case "/status/metrics"         => json(status.metricRegistry())
            case "/status/instrumentation" => json(status.instrumentation())
            case _                         => NotFound
          }

        } else {

          // Serve resources from the status page folder.
          val requestedResource = if (session.getUri() == "/") "/index.html" else session.getUri()
          val resourcePath = RootResourceDirectory + requestedResource
          val resourceStream = resourceLoader.getResourceAsStream(resourcePath)

          if (resourceStream == null) NotFound else resource(requestedResource, resourceStream)
        }
      } catch {
        case t: Throwable => serverError(t)
      }

    } else NotAllowed
  }

  override def start(): Unit = {
    setAsyncRunner(new ThreadPoolRunner(Executors.newFixedThreadPool(2)))
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
  }

  private def mimeType(resource: String): String = {
    val ResourceExtensionRegex(resourceExtension) = resource
    resourceExtension match {
      case "css"   => "text/css"
      case "js"    => "application/javascript"
      case "ico"   => "image/x-icon"
      case "svg"   => "image/svg+xml"
      case "html"  => "text/html"
      case "woff2" => "font/woff2"
      case _       => "text/plain"
    }
  }

  private def json[T](instance: T)(implicit marshalling: JsonMarshalling[T]) = {
    val builder = new java.lang.StringBuilder()
    marshalling.toJson(instance, builder)

    val response = NanoHTTPD.newFixedLengthResponse(StatusCode.OK, "application/json", builder.toString())
    response.closeConnection(true)
    response
  }

  private def resource(name: String, stream: InputStream) = {
    val response = NanoHTTPD.newChunkedResponse(StatusCode.OK, mimeType(name), stream)
    response.closeConnection(true)
    response
  }

  private def serverError(cause: Throwable) = NanoHTTPD.newFixedLengthResponse(
    StatusCode.INTERNAL_ERROR,
    NanoHTTPD.MIME_PLAINTEXT,
    "Failed to serve request due to: \n\n" + cause.getStackTrace.mkString("", EOL, EOL)
  )

  private val NotAllowed = NanoHTTPD.newFixedLengthResponse(
    StatusCode.METHOD_NOT_ALLOWED,
    NanoHTTPD.MIME_PLAINTEXT,
    "Only GET requests are allowed."
  )

  private val NotFound = NanoHTTPD.newFixedLengthResponse(
    StatusCode.NOT_FOUND,
    NanoHTTPD.MIME_PLAINTEXT,
    "The requested resource was not found."
  )

  // Closing the connections will ensure that the thread pool will not be exhausted by keep alive
  // connections from the browsers.
  NotAllowed.closeConnection(true)
  NotFound.closeConnection(true)

  /**
    * AsyncRunner that uses a thread pool for handling requests rather than spawning a new thread for each request (as
    * the default runner does).
    */
  private class ThreadPoolRunner(executorService: ExecutorService) extends NanoHTTPD.AsyncRunner {
    final private val _openRequests = Collections.synchronizedList(new java.util.LinkedList[NanoHTTPD#ClientHandler]())

    override def closeAll(): Unit =
      _openRequests.asScala.foreach(_.close())

    override def closed(clientHandler: NanoHTTPD#ClientHandler): Unit =
      _openRequests.remove(clientHandler)

    override def exec(clientHandler: NanoHTTPD#ClientHandler): Unit = {
      executorService.submit(clientHandler)
      _openRequests.add(clientHandler)
    }
  }
}
