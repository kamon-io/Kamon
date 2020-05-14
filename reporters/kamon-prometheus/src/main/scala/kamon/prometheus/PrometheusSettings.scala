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
    customBuckets: Map[String, Seq[java.lang.Double]],
    includeEnvironmentTags: Boolean,
    summarySettings: SummarySettings
  )

  case class SummarySettings(
    exclusive: Boolean,
    quantiles: Seq[java.lang.Double],
    metricMatchers: Seq[Glob]
  )

  def readSettings(prometheusConfig: Config): Generic = {
    Generic(
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala.toSeq,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala.toSeq,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala.toSeq,
      customBuckets = readCustomBuckets(prometheusConfig.getConfig("buckets.custom")),
      includeEnvironmentTags = prometheusConfig.getBoolean("include-environment-tags"),
      summarySettings = SummarySettings(
        exclusive = prometheusConfig.getBoolean("summary.exclusive"),
        quantiles = prometheusConfig.getDoubleList("summary.quantiles").asScala.toSeq,
        metricMatchers = prometheusConfig.getStringList("summary.metrics").asScala.map(Glob).toSeq
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
