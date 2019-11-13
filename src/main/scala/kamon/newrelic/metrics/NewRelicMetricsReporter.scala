/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.metrics.{MetricBatch, MetricBatchSender}
import com.newrelic.telemetry.{Attributes, SimpleMetricBatchSender}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

class NewRelicMetricsReporter(senderBuilder: () => MetricBatchSender = () => NewRelicMetricsReporter.buildSender()) extends MetricReporter {

  private val logger = LoggerFactory.getLogger(classOf[NewRelicMetricsReporter])
  @volatile private var commonAttributes = buildCommonAttributes(Kamon.config())
  @volatile private var sender: MetricBatchSender = senderBuilder()

  private def buildCommonAttributes(config: Config) = {
    new Attributes()
      .put("instrumentation.source", "kamon-agent")
      .put("service.name", config.getConfig("kamon.environment").getString("service"))
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot) = {
    logger.warn("NewRelicMetricReporter reportPeriodSnapshot...")
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

    val metrics = Seq(counters, gauges, histogramMetrics, timerMetrics).flatten.asJava
    val batch = new MetricBatch(metrics, commonAttributes)

    sender.sendBatch(batch)
  }

  override def stop(): Unit = {}

  override def reconfigure(newConfig: Config): Unit = {
    commonAttributes = buildCommonAttributes(newConfig)
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
    SimpleMetricBatchSender.builder(nrInsightsInsertKey)
      .enableAuditLogging()
      .build()
  }
}
