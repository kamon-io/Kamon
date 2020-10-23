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
package prometheus

import java.time.Duration

import com.typesafe.config.Config
import kamon.metric._
import kamon.module.{MetricReporter, Module, ModuleFactory}
import kamon.prometheus.PrometheusReporter.DefaultConfigPath
import kamon.prometheus.embeddedhttp.EmbeddedHttpServer
import org.slf4j.LoggerFactory

class PrometheusReporter(configPath: String = DefaultConfigPath, initialConfig: Config = Kamon.config()) extends MetricReporter with ScrapeSource {

  import PrometheusReporter.Settings.readSettings
  import PrometheusSettings.environmentTags

  private val _logger = LoggerFactory.getLogger(classOf[PrometheusReporter])
  private var _embeddedHttpServer: Option[EmbeddedHttpServer] = None
  private val _snapshotAccumulator = PeriodSnapshot.accumulator(Duration.ofDays(365 * 5), Duration.ZERO, Duration.ofDays(365 * 5))

  @volatile private var _preparedScrapeData: String =
    "# The kamon-prometheus module didn't receive any data just yet.\n"

  @volatile private var _config = initialConfig
  @volatile private var _reporterSettings = readSettings(initialConfig.getConfig(configPath))

  {
    startEmbeddedServerIfEnabled()
  }

  override def stop(): Unit =
    stopEmbeddedServerIfStarted()

  override def reconfigure(newConfig: Config): Unit = {
    _reporterSettings = readSettings(newConfig.getConfig(configPath))
    _config = newConfig
    stopEmbeddedServerIfStarted()
    startEmbeddedServerIfEnabled()
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    _snapshotAccumulator.add(snapshot)
    val currentData = _snapshotAccumulator.peek()
    val scrapeDataBuilder = new ScrapeDataBuilder(_reporterSettings.generic, environmentTags(_reporterSettings.generic))

    scrapeDataBuilder.appendCounters(currentData.counters)
    scrapeDataBuilder.appendGauges(currentData.gauges)
    scrapeDataBuilder.appendHistograms(currentData.histograms)
    scrapeDataBuilder.appendHistograms(currentData.timers)
    scrapeDataBuilder.appendHistograms(currentData.rangeSamplers)
    _preparedScrapeData = scrapeDataBuilder.build()
  }

  def scrapeData(): String =
    _preparedScrapeData

  private def startEmbeddedServerIfEnabled(): Unit = {
    if (_reporterSettings.startEmbeddedServer) {
      val server = _reporterSettings.createEmbeddedHttpServerClass()
          .getConstructor(Array[Class[_]](classOf[String], classOf[Int], classOf[ScrapeSource], classOf[Config]): _*)
          .newInstance(_reporterSettings.embeddedServerHostname, _reporterSettings.embeddedServerPort:Integer, this, _config)
      _logger.info(s"Started the embedded HTTP server on http://${_reporterSettings.embeddedServerHostname}:${_reporterSettings.embeddedServerPort}")
      _embeddedHttpServer = Some(server)
    }
  }

  private def stopEmbeddedServerIfStarted(): Unit =
    _embeddedHttpServer.foreach(_.stop())
}

object PrometheusReporter {
  final val DefaultConfigPath = "kamon.prometheus"

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new PrometheusReporter(DefaultConfigPath, settings.config)
  }

  def create(): PrometheusReporter = {
    new PrometheusReporter()
  }

  case class Settings(
                         startEmbeddedServer: Boolean,
                         embeddedServerHostname: String,
                         embeddedServerPort: Int,
                         embeddedServerImpl: String,
                         generic: PrometheusSettings.Generic
                     ) {
    def createEmbeddedHttpServerClass(): Class[_ <: EmbeddedHttpServer] = {
      val clz = embeddedServerImpl match {
        case "sun" => "kamon.prometheus.embeddedhttp.SunEmbeddedHttpServer"
        case fqcn => fqcn
      }
      Class.forName(clz).asInstanceOf[Class[_ <: EmbeddedHttpServer]]
    }
  }

  object Settings {

    def readSettings(prometheusConfig: Config): PrometheusReporter.Settings = {
      PrometheusReporter.Settings(
        startEmbeddedServer = prometheusConfig.getBoolean("start-embedded-http-server"),
        embeddedServerHostname = prometheusConfig.getString("embedded-server.hostname"),
        embeddedServerPort = prometheusConfig.getInt("embedded-server.port"),
        embeddedServerImpl = prometheusConfig.getString("embedded-server.impl"),
        generic = PrometheusSettings.readSettings(prometheusConfig)
      )
    }
  }
}

trait ScrapeSource {
  def scrapeData(): String
}

