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

package kamon.prometheus

import java.time.Duration

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import org.slf4j.LoggerFactory

class PrometheusPushgatewayReporter(
  configPath: String,
  pushgatewayPath: String,
  @volatile private var httpClientFactory: Config => HttpClient
) extends MetricReporter {

  private val _logger = LoggerFactory.getLogger(classOf[PrometheusPushgatewayReporter])
  private val _snapshotAccumulator =
    PeriodSnapshot.accumulator(Duration.ofDays(365 * 5), Duration.ZERO, Duration.ofDays(365 * 5))

  @volatile private var httpClient: HttpClient = _
  @volatile private var settings: PrometheusSettings.Generic = _

  {
    reconfigure(Kamon.config())
  }

  def this(httpClientFactory: Config => HttpClient) =
    this("kamon.prometheus", "pushgateway", httpClientFactory)

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    _snapshotAccumulator.add(snapshot)
    val currentData = _snapshotAccumulator.peek()

    val scrapeDataBuilder = new ScrapeDataBuilder(settings, PrometheusSettings.environmentTags(settings))
    scrapeDataBuilder.appendCounters(currentData.counters)
    scrapeDataBuilder.appendGauges(currentData.gauges)
    scrapeDataBuilder.appendHistograms(currentData.histograms)
    scrapeDataBuilder.appendHistograms(currentData.timers)
    scrapeDataBuilder.appendHistograms(currentData.rangeSamplers)

    val message = scrapeDataBuilder.build()

    httpClient.doPost("text/plain; version=0.0.4", message.toCharArray.map(_.toByte)).failed.foreach(exception =>
      _logger.error("Failed to send metrics to Prometheus Pushgateway", exception)
    )
  }

  override def stop(): Unit = ()

  override def reconfigure(newConfig: Config): Unit = {
    this.settings = PrometheusSettings.readSettings(newConfig.getConfig(configPath))
    this.httpClient = httpClientFactory(newConfig.getConfig(configPath).getConfig(pushgatewayPath))
    _logger.info(s"Connection to Prometheus Pushgateway on url ${this.httpClient.apiUrl}")
  }
}

object PrometheusPushgatewayReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      new PrometheusPushgatewayReporter((config: Config) => new HttpClient(config))
    }
  }
}
