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

package kamon.newrelic

import com.typesafe.config.Config
import kamon.newrelic.NewRelicConfig.NewRelicApiKey
import kamon.newrelic.NewRelicConfig.NewRelicApiKey.{InsightsInsertKey, LicenseKey}

import java.net.URL
import java.time.Duration


private case class NewRelicConfig(
                                   apiKey: NewRelicApiKey,
                                   enableAuditLogging: Boolean,
                                   userAgent: String,
                                   callTimeout: Duration,
                                   spanIngestUri: Option[URL],
                                   metricIngestUri: Option[URL]
                                 )

private object NewRelicConfig {

  def fromConfig(config: Config): NewRelicConfig = {
    val nrConfig = config.getConfig("kamon.newrelic")

    // TODO maybe some validation around these values?
    val apiKey = if (nrConfig.hasPath("license-key")) {
      LicenseKey(nrConfig.getString("license-key"))
    } else {
      InsightsInsertKey(nrConfig.getString("nr-insights-insert-key"))
    }
    val enableAuditLogging = nrConfig.getBoolean("enable-audit-logging")
    val userAgent = s"newrelic-kamon-reporter/${LibraryVersion.version}"
    val callTimeout = Duration.ofSeconds(5)
    val spanIngestUri = getUrlOption(nrConfig, "span-ingest-uri")
    val metricIngestUri = getUrlOption(nrConfig, "metric-ingest-uri")
    NewRelicConfig(apiKey, enableAuditLogging, userAgent, callTimeout, spanIngestUri, metricIngestUri)
  }

  private def getUrlOption(config: Config, path: String): Option[URL] = {
    if (config.hasPath(path)) Some(new URL(config.getString(path))) else None
  }

  sealed trait NewRelicApiKey {
    def apiKeyAndUseLicenseKey: (String, Boolean) = this match {
      case LicenseKey(licenceKey) => (licenceKey, true)
      case InsightsInsertKey(insightsInsertKey) => (insightsInsertKey, false)
    }
  }

  object NewRelicApiKey {
    case class LicenseKey(licenseKey: String) extends NewRelicApiKey

    case class InsightsInsertKey(insightsInsertKey: String) extends NewRelicApiKey
  }
}
