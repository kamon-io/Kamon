package kamon.prometheus.embeddedhttp

import com.typesafe.config.Config
import kamon.prometheus.ScrapeSource

abstract class EmbeddedHttpServer(hostname: String, port: Int, scrapeSource: ScrapeSource, config: Config) {
  def stop(): Unit
}