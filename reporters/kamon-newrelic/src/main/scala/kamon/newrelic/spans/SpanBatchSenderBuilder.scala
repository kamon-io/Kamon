/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import java.time.Duration

import com.newrelic.telemetry.SimpleSpanBatchSender
import com.newrelic.telemetry.spans.SpanBatchSender
import com.typesafe.config.Config
import kamon.newrelic.LibraryVersion
import org.slf4j.LoggerFactory

trait SpanBatchSenderBuilder {
  def build(config: Config): SpanBatchSender
}

class SimpleSpanBatchSenderBuilder() extends SpanBatchSenderBuilder {

  private val logger = LoggerFactory.getLogger(classOf[SpanBatchSenderBuilder])

  /**
    * SpanBatchSender responsible for sending batches of Spans to New Relic using the Telemetry SDK
    *
    * @param config User defined config
    * @return New Relic SpanBatchSender
    */
  override def build(config: Config) = {
    logger.warn("NewRelicSpanReporter buildReporter...")
    val nrConfig = config.getConfig("kamon.newrelic")
    // TODO maybe some validation around these values?
    val nrInsightsInsertKey = nrConfig.getString("nr-insights-insert-key")

    if (nrInsightsInsertKey.equals("none"))
      logger.error(
        "No Insights Insert API Key defined for the kamon.newrelic.nr-insights-insert-key config setting. " +
          "No spans will be sent to New Relic."
      )

    SimpleSpanBatchSender
      .builder(nrInsightsInsertKey, Duration.ofSeconds(5))
      .enableAuditLogging()
      .secondaryUserAgent("newrelic-kamon-reporter", LibraryVersion.version)
      .build()
  }
}
