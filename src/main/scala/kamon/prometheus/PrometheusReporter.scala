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

package kamon
package prometheus

import java.time.Duration

import com.typesafe.config.Config
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.{Response, newFixedLengthResponse}
import kamon.metric._
import kamon.module.{MetricReporter, Module, ModuleFactory}
import org.slf4j.LoggerFactory

class PrometheusReporter(configPath: String) extends MetricReporter {

  import PrometheusReporter.Settings.readSettings
  import PrometheusSettings.environmentTags

  private val _logger = LoggerFactory.getLogger(classOf[PrometheusReporter])
  private var _embeddedHttpServer: Option[EmbeddedHttpServer] = None
  private val _snapshotAccumulator = PeriodSnapshot.accumulator(Duration.ofDays(365 * 5), Duration.ZERO)

  @volatile private var _preparedScrapeData: String =
    "# The kamon-prometheus module didn't receive any data just yet.\n"

  def this() =
    this("kamon.prometheus")

  {
    val initialSettings = readSettings(Kamon.config().getConfig(configPath))
    if(initialSettings.startEmbeddedServer)
      startEmbeddedServer(initialSettings)
  }

  override def stop(): Unit =
    stopEmbeddedServer()

  override def reconfigure(newConfig: Config): Unit = {
    val config = readSettings(newConfig.getConfig(configPath))

    stopEmbeddedServer()
    if(config.startEmbeddedServer) {
      startEmbeddedServer(config)
    }
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    _snapshotAccumulator.add(snapshot)
    val currentData = _snapshotAccumulator.peek()
    val reporterConfiguration = readSettings(Kamon.config().getConfig(configPath)).generic
    val scrapeDataBuilder = new ScrapeDataBuilder(reporterConfiguration, environmentTags(reporterConfiguration))

    scrapeDataBuilder.appendCounters(currentData.counters)
    scrapeDataBuilder.appendGauges(currentData.gauges)
    scrapeDataBuilder.appendHistograms(currentData.histograms)
    scrapeDataBuilder.appendHistograms(currentData.timers)
    scrapeDataBuilder.appendHistograms(currentData.rangeSamplers)
    _preparedScrapeData = scrapeDataBuilder.build()
  }

  def scrapeData(): String =
    _preparedScrapeData

  class EmbeddedHttpServer(hostname: String, port: Int) extends NanoHTTPD(hostname, port) {
    override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
      newFixedLengthResponse(Response.Status.OK, "text/plain; version=0.0.4; charset=utf-8", scrapeData())
    }
  }

  private def startEmbeddedServer(config: PrometheusReporter.Settings): Unit = {
    val server = new EmbeddedHttpServer(config.embeddedServerHostname, config.embeddedServerPort)
    server.start()

    _logger.info(s"Started the embedded HTTP server on http://${config.embeddedServerHostname}:${config.embeddedServerPort}")
    _embeddedHttpServer = Some(server)
  }

  private def stopEmbeddedServer(): Unit =
    _embeddedHttpServer.foreach(_.stop())
}

object PrometheusReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new PrometheusReporter()
  }

  def create(): PrometheusReporter = {
    new PrometheusReporter()
  }

  case class Settings(
    startEmbeddedServer: Boolean,
    embeddedServerHostname: String,
    embeddedServerPort: Int,
    generic: PrometheusSettings.Generic
  )

  object Settings {

    def readSettings(prometheusConfig: Config): PrometheusReporter.Settings = {
      PrometheusReporter.Settings(
        startEmbeddedServer = prometheusConfig.getBoolean("start-embedded-http-server"),
        embeddedServerHostname = prometheusConfig.getString("embedded-server.hostname"),
        embeddedServerPort = prometheusConfig.getInt("embedded-server.port"),
        generic = PrometheusSettings.readSettings(prometheusConfig)
      )
    }
  }
}
