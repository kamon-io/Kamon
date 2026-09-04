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

package kamon.prometheus.embeddedhttp

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.Config
import kamon.prometheus.ScrapeSource
import kamon.prometheus.embeddedhttp.SunEmbeddedHttpServer.shouldUseCompression

import java.io.OutputStream
import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.StandardCharsets
import java.util
import java.util.zip.GZIPOutputStream
import scala.collection.JavaConverters._
import scala.collection.mutable

class SunEmbeddedHttpServer(hostname: String, port: Int, path: String, scrapeSource: ScrapeSource, config: Config)
    extends EmbeddedHttpServer(hostname, port, scrapeSource, config) {
  private val server = {
    val s = HttpServer.create(new InetSocketAddress(InetAddress.getByName(hostname), port), 0)
    s.setExecutor(null)
    val handler = new HttpHandler {
      override def handle(httpExchange: HttpExchange): Unit = {
        if (httpExchange.getRequestURI.getPath == path) {
          val data = scrapeSource.scrapeData()
          val bytes = data.getBytes(StandardCharsets.UTF_8)
          var os: OutputStream = null
          try {
            if (shouldUseCompression(httpExchange)) {
              httpExchange.getResponseHeaders.set("Content-Encoding", "gzip")
              httpExchange.sendResponseHeaders(200, 0)
              os = new GZIPOutputStream(httpExchange.getResponseBody)
              os.write(bytes)
            } else {
              os = httpExchange.getResponseBody
              httpExchange.sendResponseHeaders(200, bytes.length)
              os.write(bytes)
            }
          } finally Option(os).map(_.close())
        } else httpExchange.sendResponseHeaders(404, -1)
      }
    }

    s.createContext(path, handler)
    s.start()
    s
  }

  def stop(): Unit = server.stop(0)
}

object SunEmbeddedHttpServer {
  def shouldUseCompression(httpExchange: HttpExchange): Boolean = {
    httpExchange.getRequestHeaders
      .asScala.get("Accept-Encoding")
      .map(extractEncodings)
      .exists(_.contains("gzip"))
  }

  private def extractEncodings(headerList: util.List[String]): mutable.Buffer[String] = {
    headerList.asScala
      .flatMap(_.split(","))
      .map(_.trim().toLowerCase())
  }
}
