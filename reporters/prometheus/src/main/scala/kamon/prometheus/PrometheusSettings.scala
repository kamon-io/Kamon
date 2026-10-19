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

import com.typesafe.config.{Config, ConfigUtil}
import kamon.tag.TagSet
import kamon.util.Filter.Glob
import kamon.{Kamon, UtilsOnConfig}

import scala.collection.JavaConverters._

object PrometheusSettings {

  case class Generic(
    defaultBuckets: Seq[java.lang.Double],
    timeBuckets: Seq[java.lang.Double],
    informationBuckets: Seq[java.lang.Double],
    percentageBuckets: Seq[java.lang.Double],
    customBuckets: Map[String, Seq[java.lang.Double]],
    includeEnvironmentTags: Boolean,
    summarySettings: SummarySettings,
    gaugeSettings: GaugeSettings
  )

  case class SummarySettings(
    quantiles: Seq[java.lang.Double],
    metricMatchers: Seq[Glob]
  )

  case class GaugeSettings(metricMatchers: Seq[Glob])

  def readSettings(prometheusConfig: Config): Generic = {
    Generic(
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala.toSeq,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala.toSeq,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala.toSeq,
      percentageBuckets = prometheusConfig.getDoubleList("buckets.percentage-buckets").asScala.toSeq,
      customBuckets = readCustomBuckets(prometheusConfig.getConfig("buckets.custom")),
      includeEnvironmentTags = prometheusConfig.getBoolean("include-environment-tags"),
      summarySettings = SummarySettings(
        quantiles = prometheusConfig.getDoubleList("summaries.quantiles").asScala.toSeq,
        metricMatchers = prometheusConfig.getStringList("summaries.metrics").asScala.map(Glob).toSeq
      ),
      gaugeSettings = GaugeSettings(
        metricMatchers = prometheusConfig.getStringList("gauges.metrics").asScala.map(Glob).toSeq
      )
    )
  }

  def environmentTags(reporterConfiguration: Generic): TagSet =
    if (reporterConfiguration.includeEnvironmentTags) Kamon.environment.tags else TagSet.Empty

  def readCustomBuckets(customBuckets: Config): Map[String, Seq[java.lang.Double]] =
    customBuckets
      .topLevelKeys
      .map(k => (k, customBuckets.getDoubleList(ConfigUtil.quoteString(k)).asScala.toSeq))
      .toMap
}
