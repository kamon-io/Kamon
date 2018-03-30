/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.prometheus

import java.time.Duration

import com.typesafe.config.Config
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.{Response, newFixedLengthResponse}
import kamon.{Kamon, MetricReporter}
import kamon.metric._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class PrometheusReporter extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[PrometheusReporter])
  private var embeddedHttpServer: Option[EmbeddedHttpServer] = None
  private val snapshotAccumulator = new PeriodSnapshotAccumulator(Duration.ofDays(365 * 5), Duration.ZERO)

  @volatile private var preparedScrapeData: String =
    "# The kamon-prometheus module didn't receive any data just yet.\n"


  override def start(): Unit = {
    val config = readConfiguration(Kamon.config())

    if(config.startEmbeddedServer)
      startEmbeddedServer(config)
  }

  override def stop(): Unit = {
    stopEmbeddedServer()
  }

  override def reconfigure(newConfig: Config): Unit = {
    val config = readConfiguration(newConfig)
    if(config.startEmbeddedServer) {
      stopEmbeddedServer()
      startEmbeddedServer(config)
    }
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    snapshotAccumulator.add(snapshot)
    val currentData = snapshotAccumulator.peek()
    val reporterConfiguration = readConfiguration(Kamon.config())
    val scrapeDataBuilder = new ScrapeDataBuilder(reporterConfiguration, environmentTags(reporterConfiguration))

    scrapeDataBuilder.appendCounters(currentData.metrics.counters)
    scrapeDataBuilder.appendGauges(currentData.metrics.gauges)
    scrapeDataBuilder.appendHistograms(currentData.metrics.histograms)
    scrapeDataBuilder.appendHistograms(currentData.metrics.rangeSamplers)
    preparedScrapeData = scrapeDataBuilder.build()
  }

  def scrapeData(): String =
    preparedScrapeData

  class EmbeddedHttpServer(hostname: String, port: Int) extends NanoHTTPD(hostname, port) {
    override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
      newFixedLengthResponse(Response.Status.OK, "text/plain; version=0.0.4; charset=utf-8", scrapeData())
    }
  }


  private def startEmbeddedServer(config: PrometheusReporter.Configuration): Unit = {
    val server = new EmbeddedHttpServer(config.embeddedServerHostname, config.embeddedServerPort)
    server.start()

    logger.info(s"Started the embedded HTTP server on http://${config.embeddedServerHostname}:${config.embeddedServerPort}")
    embeddedHttpServer = Some(server)
  }

  private def stopEmbeddedServer(): Unit =
    embeddedHttpServer.foreach(_.stop())


  private def readConfiguration(config: Config): PrometheusReporter.Configuration = {
    val prometheusConfig = config.getConfig("kamon.prometheus")

    PrometheusReporter.Configuration(
      startEmbeddedServer = prometheusConfig.getBoolean("start-embedded-http-server"),
      embeddedServerHostname = prometheusConfig.getString("embedded-server.hostname"),
      embeddedServerPort = prometheusConfig.getInt("embedded-server.port"),
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala,
      includeEnvironmentTags = prometheusConfig.getBoolean("include-environment-tags")
    )
  }

  private def environmentTags(reporterConfiguration: PrometheusReporter.Configuration) =
    if (reporterConfiguration.includeEnvironmentTags) Kamon.environment.tags else Map.empty[String, String]
}

object PrometheusReporter {
  case class Configuration(startEmbeddedServer: Boolean, embeddedServerHostname: String, embeddedServerPort: Int,
    defaultBuckets: Seq[java.lang.Double], timeBuckets: Seq[java.lang.Double], informationBuckets: Seq[java.lang.Double],
    includeEnvironmentTags: Boolean)
}
