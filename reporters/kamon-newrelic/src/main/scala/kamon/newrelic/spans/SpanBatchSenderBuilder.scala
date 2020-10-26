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

package kamon.newrelic.spans

import java.net.URL
import java.time.Duration

import com.newrelic.telemetry.{OkHttpPoster, SenderConfiguration, SimpleSpanBatchSender}
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
    val senderConfig = buildConfig(config)
    SpanBatchSender.create(senderConfig)
  }

  def buildConfig(config: Config): SenderConfiguration = {
    val nrConfig = config.getConfig("kamon.newrelic")
    // TODO maybe some validation around these values?
    val nrInsightsInsertKey = nrConfig.getString("nr-insights-insert-key")

    if (nrInsightsInsertKey.equals("none")) {
      logger.error("No Insights Insert API Key defined for the kamon.newrelic.nr-insights-insert-key config setting. " +
        "No spans will be sent to New Relic.")
    }
    val enableAuditLogging = nrConfig.getBoolean("enable-audit-logging")

    val userAgent = s"newrelic-kamon-reporter/${LibraryVersion.version}"
    val callTimeout = Duration.ofSeconds(5)

    val senderConfig = SpanBatchSender.configurationBuilder()
      .apiKey(nrInsightsInsertKey)
      .httpPoster(new OkHttpPoster(callTimeout))
      .secondaryUserAgent(userAgent)
      .auditLoggingEnabled(enableAuditLogging)

    if (nrConfig.hasPath("span-ingest-uri")) {
      val uriOverride = nrConfig.getString("span-ingest-uri")
      senderConfig.endpointWithPath(new URL(uriOverride))
    }

    senderConfig.build
  }
}
