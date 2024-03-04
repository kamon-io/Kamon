/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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
package kamon.otel

import com.typesafe.config.{Config, ConfigUtil}
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.resources.Resource
import kamon.metric.MeasurementUnit.Dimension
import kamon.{Kamon, UtilsOnConfig}
import kamon.metric.{MeasurementUnit, PeriodSnapshot}
import kamon.module.{MetricReporter, Module, ModuleFactory}
import kamon.otel.HistogramFormat.Explicit
import kamon.otel.OpenTelemetryConfiguration.Component.Metrics
import kamon.status.Status
import org.slf4j.LoggerFactory

import java.util
import java.lang.{Double => JDouble}
import java.util.{Collection => JCollection}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class BucketConfig[T](
  defaultBuckets: T,
  timeBuckets: T,
  informationBuckets: T,
  percentageBuckets: T,
  customBuckets: Map[String, T]
)

object Buckets {

  private def readCustomBuckets(customBuckets: Config): Map[String, Seq[java.lang.Double]] =
    customBuckets
      .topLevelKeys
      .map(k => (k, customBuckets.getDoubleList(ConfigUtil.quoteString(k)).asScala.toSeq))
      .toMap

  private def readCustomBucketsExpo(customBuckets: Config): Map[String, Int] =
    customBuckets
      .topLevelKeys
      .map(k => (k, customBuckets.getInt(ConfigUtil.quoteString(k))))
      .toMap

  def parseBucketConfig(newConfig: Config): BucketConfig[Seq[JDouble]] = BucketConfig[Seq[JDouble]](
    newConfig.getDoubleList("default-buckets").asScala.toSeq,
    newConfig.getDoubleList("time-buckets").asScala.toSeq,
    informationBuckets = newConfig.getDoubleList("information-buckets").asScala.toSeq,
    percentageBuckets = newConfig.getDoubleList("percentage-buckets").asScala.toSeq,
    readCustomBuckets(newConfig.getConfig("custom"))
  )

  def resolveBucketConfiguration[T](bucketConfig: BucketConfig[T])(metricName: String, unit: MeasurementUnit): T =
    bucketConfig.customBuckets.getOrElse(
      metricName,
      unit.dimension match {
        case Dimension.Time        => bucketConfig.timeBuckets
        case Dimension.Information => bucketConfig.informationBuckets
        case Dimension.Percentage  => bucketConfig.percentageBuckets
        case _                     => bucketConfig.defaultBuckets
      }
    )

  def parseExpoBucketConfig(newConfig: Config): BucketConfig[Int] = BucketConfig[Int](
    newConfig.getInt("default-bucket-count"),
    newConfig.getInt("time-bucket-count"),
    informationBuckets = newConfig.getInt("information-bucket-count"),
    percentageBuckets = newConfig.getInt("percentage-bucket-count"),
    readCustomBucketsExpo(newConfig.getConfig("custom"))
  )

}
object OpenTelemetryMetricsReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryMetricsReporter])
  private val kamonSettings: Status.Settings = Kamon.status().settings()

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Metrics Reporter")

      val module = new OpenTelemetryMetricsReporter(OtlpMetricsService.apply)(settings.executionContext)
      module.reconfigure(settings.config)
      module
    }
  }
}

import kamon.otel.OpenTelemetryMetricsReporter._

/**
 * Converts internal Kamon metrics to OpenTelemetry format and sends to a configured OpenTelemetry endpoint using gRPC or REST.
 */
class OpenTelemetryMetricsReporter(metricsServiceFactory: OpenTelemetryConfiguration => MetricsService)(implicit
  ec: ExecutionContext
) extends MetricReporter {
  private var metricsService: Option[MetricsService] = None
  private var metricsConverterFunc: PeriodSnapshot => JCollection[MetricData] = (_ => new util.ArrayList[MetricData](0))

  def isEmpty(snapshot: PeriodSnapshot): Boolean =
    snapshot.gauges.isEmpty && snapshot.timers.isEmpty && snapshot.counters.isEmpty && snapshot.histograms.isEmpty && snapshot.rangeSamplers.isEmpty

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    if (!isEmpty(snapshot)) {
      metricsService.foreach(ts =>
        ts.exportMetrics(metricsConverterFunc(snapshot)).onComplete {
          case Success(_) => logger.debug("Successfully exported metrics")

          // TODO is there result for which a retry is relevant? Perhaps a glitch in the receiving service
          // Keeping logs to debug as the underlying exporter will log if it fails to export metrics, and the failure isn't surfaced in the response anyway
          case Failure(t) => logger.debug("Failed to export metrics", t)
        }
      )
    }
  }

  override def reconfigure(newConfig: Config): Unit = {
    logger.info("Reconfigure OpenTelemetry Metrics Reporter")

    // pre-generate the function for converting Kamon metrics to proto metrics
    val attributes: Map[String, String] = OpenTelemetryConfiguration.getAttributes(newConfig)
    val resource: Resource = OpenTelemetryConfiguration.buildResource(attributes)
    val config = OpenTelemetryConfiguration(newConfig, Metrics)
    val histogramFormat = config.histogramFormat.getOrElse {
      logger.warn("Missing histogram-format from metrics configuration, defaulting to Explicit")
      Explicit
    }

    val explicitBucketConfig = Buckets.parseBucketConfig(newConfig.getConfig("kamon.otel.explicit-histo-boundaries"))
    val exponentialBucketConfig =
      Buckets.parseExpoBucketConfig(newConfig.getConfig("kamon.otel.exponential-histo-boundaries"))

    val resolveExplicitBucketConfiguration = Buckets.resolveBucketConfiguration(explicitBucketConfig) _

    val resolveExponentialBucketConfiguration = Buckets.resolveBucketConfiguration(exponentialBucketConfig) _

    this.metricsConverterFunc = MetricsConverter.convert(
      resource,
      kamonSettings.version,
      histogramFormat,
      resolveExplicitBucketConfiguration,
      resolveExponentialBucketConfiguration
    )

    this.metricsService = Option(metricsServiceFactory.apply(config))
  }

  override def stop(): Unit = {
    logger.info("Stopping OpenTelemetry Metrics Reporter")
    this.metricsService.foreach(_.close())
    this.metricsService = None
  }

}
