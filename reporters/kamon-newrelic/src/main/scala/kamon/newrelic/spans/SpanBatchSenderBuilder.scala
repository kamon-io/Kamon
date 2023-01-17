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

import com.newrelic.telemetry.{OkHttpPoster, SenderConfiguration}
import com.newrelic.telemetry.spans.SpanBatchSender
import com.typesafe.config.Config
import kamon.newrelic.NewRelicConfig.NewRelicApiKey.InsightsInsertKey
import kamon.newrelic.NewRelicConfig
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
    val nrConfig = NewRelicConfig.fromConfig(config)
    if (nrConfig.apiKey == InsightsInsertKey("none")) {
      logger.error("One of kamon.newrelic.license-key or kamon.newrelic.nr-insights-insert-key config settings should be defined. " +
        "No spans will be sent to New Relic.")
    }
    val senderConfig = SpanBatchSender.configurationBuilder()
      .apiKey(nrConfig.apiKey.value)
      .useLicenseKey(nrConfig.apiKey.isLicenseKey)
      .httpPoster(new OkHttpPoster(nrConfig.callTimeout))
      .secondaryUserAgent(nrConfig.userAgent)
      .auditLoggingEnabled(nrConfig.enableAuditLogging)
    nrConfig.spanIngestUri.foreach(senderConfig.endpoint)
    senderConfig.build
  }
}
