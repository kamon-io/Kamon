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

package kamon.newrelic.metrics

import com.newrelic.telemetry.{OkHttpPoster, SenderConfiguration}
import com.newrelic.telemetry.metrics.{MetricBatch, MetricBatchSender}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import kamon.newrelic.AttributeBuddy.buildCommonAttributes
import kamon.newrelic.NewRelicConfig
import kamon.status.Environment
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class NewRelicMetricsReporter(senderBuilder: () => MetricBatchSender = () => NewRelicMetricsReporter.buildSender()) extends MetricReporter {

  private val logger = LoggerFactory.getLogger(classOf[NewRelicMetricsReporter])
  @volatile private var commonAttributes = buildCommonAttributes(Kamon.environment)
  @volatile private var sender: MetricBatchSender = senderBuilder()

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot) = {
    logger.debug("NewRelicMetricReporter reportPeriodSnapshot...")
    val periodStartTime = snapshot.from.toEpochMilli
    val periodEndTime = snapshot.to.toEpochMilli

    val counters = snapshot.counters.flatMap { counter =>
      NewRelicCounters(periodStartTime, periodEndTime, counter)
    }
    val gauges = snapshot.gauges.flatMap { gauge =>
      NewRelicGauges(periodEndTime, gauge)
    }
    val histogramMetrics = snapshot.histograms.flatMap { histogram =>
      NewRelicDistributionMetrics(periodStartTime, periodEndTime, histogram, "histogram")
    }
    val timerMetrics = snapshot.timers.flatMap { timer =>
      NewRelicDistributionMetrics(periodStartTime, periodEndTime, timer, "timer")
    }
    val rangeSamplerMetrics = snapshot.rangeSamplers.flatMap { rangeSampler =>
      NewRelicDistributionMetrics(periodStartTime, periodEndTime, rangeSampler, "rangeSampler")
    }

    val metrics = Seq(counters, gauges, histogramMetrics, timerMetrics, rangeSamplerMetrics).flatten.asJava
    val batch = new MetricBatch(metrics, commonAttributes)

    sender.sendBatch(batch)
  }

  override def stop(): Unit = {}

  override def reconfigure(newConfig: Config): Unit = {
    reconfigure(Kamon.environment)
  }

  //exposed for testing
  def reconfigure(environment: Environment): Unit = {
    commonAttributes = buildCommonAttributes(environment)
    sender = senderBuilder()
  }

}

object NewRelicMetricsReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new NewRelicMetricsReporter()
  }

  def buildSender(config: Config = Kamon.config()): MetricBatchSender = {
    val senderConfig = buildSenderConfig(config)
    MetricBatchSender.create(senderConfig);
  }

  def buildSenderConfig(config: Config): SenderConfiguration = {
    val nrConfig = NewRelicConfig.fromConfig(config)
    val senderConfig = MetricBatchSender.configurationBuilder()
      .apiKey(nrConfig.apiKey.value)
      .useLicenseKey(nrConfig.apiKey.isLicenseKey)
      .httpPoster(new OkHttpPoster(nrConfig.callTimeout))
      .auditLoggingEnabled(nrConfig.enableAuditLogging)
      .secondaryUserAgent(nrConfig.userAgent)
    nrConfig.metricIngestUri.foreach(senderConfig.endpoint)
    senderConfig.build()
  }
}
