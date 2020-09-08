package kamon.prometheus.embeddedhttp

import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.StandardCharsets

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.Config
import kamon.prometheus.ScrapeSource

class SunEmbeddedHttpServer(hostname: String, port: Int, scrapeSource: ScrapeSource, config: Config) extends EmbeddedHttpServer(hostname, port, scrapeSource, config) {
  private val server = {
    val s = HttpServer.create(new InetSocketAddress(InetAddress.getByName(hostname), port), 0)
    s.setExecutor(null)
    val handler = new HttpHandler {
      override def handle(httpExchange: HttpExchange): Unit = {
        val data = scrapeSource.scrapeData()
        val bytes = data.getBytes(StandardCharsets.UTF_8)
        httpExchange.sendResponseHeaders(200, bytes.length)
        val os = httpExchange.getResponseBody
        try {
          os.write(bytes)
        }
        finally
          os.close()
      }

    }
    s.createContext("/metrics", handler)
    s.createContext("/", handler)
    s.start()
    s
  }

  def stop(): Unit = server.stop(0)
}
