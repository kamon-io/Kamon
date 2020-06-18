/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import java.net.{URI, URL}

import com.newrelic.telemetry.SimpleMetricBatchSender
import com.newrelic.telemetry.metrics.{MetricBatch, MetricBatchSender}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import kamon.newrelic.AttributeBuddy.buildCommonAttributes
import kamon.newrelic.LibraryVersion
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

  def buildSender(): MetricBatchSender = {
    val config = Kamon.config();
    val nrConfig = config.getConfig("kamon.newrelic")
    val nrInsightsInsertKey = nrConfig.getString("nr-insights-insert-key")
    val enableAuditLogging = nrConfig.getBoolean("enable-audit-logging")
    val userAgent = s"newrelic-kamon-reporter/${LibraryVersion.version}"

    val senderConfig = MetricBatchSender.configurationBuilder()
      .apiKey(nrInsightsInsertKey)
      .auditLoggingEnabled(enableAuditLogging)
      .secondaryUserAgent(userAgent)

    if(nrConfig.hasPath("metric-ingest-uri")) {
      val uriOverride = nrConfig.getString("metric-ingest-uri")
      senderConfig.endpointWithPath(new URL(uriOverride))
    }

    MetricBatchSender.create(senderConfig.build());
  }
}
