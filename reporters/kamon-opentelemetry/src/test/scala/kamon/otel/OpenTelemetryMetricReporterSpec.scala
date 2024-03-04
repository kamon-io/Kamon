/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.otel

import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory}
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.exponentialhistogram.ExponentialHistogramData
import kamon.Kamon
import kamon.Kamon.config
import kamon.metric._
import kamon.tag.TagSet
import kamon.testkit.Reconfigure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.{Double => JDouble}
import java.time.Instant
import java.util.{Collection => JCollection}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class OpenTelemetryMetricReporterSpec extends AnyWordSpec
    with Matchers with Reconfigure {
  reconfigure =>

  private def openTelemetryMetricsReporter(newConfig: Config = config)
    : (OpenTelemetryMetricsReporter, MockMetricsService) = {
    val metricsService = new MockMetricsService()
    val reporter = new OpenTelemetryMetricsReporter(_ => metricsService)(ExecutionContext.global)
    reporter.reconfigure(newConfig)
    (reporter, metricsService)
  }

  "the OpenTelemetryMetricsReporter" should {
    "send counter metrics" in {
      val (reporter, mockService) = openTelemetryMetricsReporter()

      val now = Instant.now()
      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
          now.minusMillis(1000),
          now,
          MetricSnapshot.ofValues[Long](
            "test.counter",
            "test",
            Metric.Settings.ForValueInstrument(MeasurementUnit.none, java.time.Duration.ZERO),
            Instrument.Snapshot.apply(TagSet.of("tag1", "value1"), 42L) :: Nil
          ) :: Nil,
          Nil,
          Nil,
          Nil,
          Nil
        )
      )
      // basic sanity
      mockService.exportMetricsServiceRequest should not be empty
      mockService.exportMetricsServiceRequest.get should have size 1
      val exportedMetrics: Seq[MetricData] = mockService.exportMetricsServiceRequest.get.asScala.toSeq
      exportedMetrics should have size 1
      val metricData = exportedMetrics.head

      // check attributes on resource
      val kamonVersion = Kamon.status().settings().version
      val attributeMap = metricData.getResource.getAttributes.asMap().asScala
      attributeMap should contain(AttributeKey.stringKey("service.name"), "kamon-test-application")
      attributeMap should contain(AttributeKey.stringKey("telemetry.sdk.name"), "kamon")
      attributeMap should contain(AttributeKey.stringKey("telemetry.sdk.language"), "scala")
      attributeMap should contain(AttributeKey.stringKey("telemetry.sdk.version"), kamonVersion)
      attributeMap should contain(AttributeKey.stringKey("service.version"), "x.x.x")
      attributeMap should contain(AttributeKey.stringKey("env"), "kamon-devint")
      attributeMap should contain(AttributeKey.stringKey("att1"), "v1")
      attributeMap should contain(AttributeKey.stringKey("att2"), "v2")
      attributeMap should contain(AttributeKey.stringKey("att3"), " a=b,c=d ")
      val host = attributeMap.get(AttributeKey.stringKey("host.name"))
      host shouldBe defined
      val instance = attributeMap.get(AttributeKey.stringKey("service.instance.id"))
      instance should contain(s"kamon-test-application@${host.get}")

      // assert instrumentation labels
      val instrumentationScopeInfo = metricData.getInstrumentationScopeInfo
      instrumentationScopeInfo.getName should be("kamon-metrics")
      instrumentationScopeInfo.getVersion should be(kamonVersion)
      instrumentationScopeInfo.getSchemaUrl should be(null)

      // check value
      metricData.getName should equal("test.counter")
      metricData.getDescription should equal("test")
      val sumData = metricData.getLongSumData
      val points = sumData.getPoints.asScala.toSeq
      points should have size 1
      points.head.getAttributes should have size 1
      points.head.getAttributes.get(AttributeKey.stringKey("tag1")) should equal("value1")
      points.head.getValue shouldEqual 42L
    }
    "send histogram metrics" in {
      val (reporter, mockService) = openTelemetryMetricsReporter()
      val now = Instant.now()
      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
          now.minusMillis(1000),
          now,
          Nil,
          Nil,
          MetricSnapshot.ofDistributions(
            "test.histogram",
            "test",
            Metric.Settings.ForDistributionInstrument(
              MeasurementUnit.none,
              java.time.Duration.ZERO,
              DynamicRange.Default
            ),
            Instrument.Snapshot(
              TagSet.from(Map("tag1" -> "value1")),
              buildHistogramDist(Seq(1L -> 2L, 2L -> 2L, 3L -> 3L, 5L -> 1L, 15L -> 1L))
            ) :: Nil
          ) :: Nil,
          Nil,
          Nil
        )
      )
      // basic sanity
      mockService.exportMetricsServiceRequest should not be empty
      mockService.exportMetricsServiceRequest.get should have size 1
      val exportedMetrics: Seq[MetricData] = mockService.exportMetricsServiceRequest.get.asScala.toSeq
      exportedMetrics should have size 1
      val metricData = exportedMetrics.head

      // check value
      metricData.getName should equal("test.histogram")
      metricData.getDescription should equal("test")
      val sumData = metricData.getHistogramData
      val points = sumData.getPoints.asScala.toSeq
      points should have size 1
      points.head.getAttributes should have size 1
      points.head.getAttributes.get(AttributeKey.stringKey("tag1")) should equal("value1")
      points.head.getMin shouldEqual 1L
      points.head.getMax shouldEqual 15L
      points.head.getSum shouldEqual 35L
      points.head.getCount shouldEqual 9L
      points.head.getBoundaries.asScala shouldEqual Seq[JDouble](1d, 2d, 3d, 4d, 10d)
      points.head.getCounts.asScala shouldEqual Seq[JDouble](2d, 2d, 3d, 0d, 1d, 1d)
    }
    "send exponential histogram metrics" in {
      val newConfig = config.withValue(
        "kamon.otel.metrics.histogram-format",
        ConfigValueFactory.fromAnyRef("base2_exponential_bucket_histogram")
      )
      val (reporter, mockService) = openTelemetryMetricsReporter(newConfig)
      val now = Instant.now()
      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
          now.minusMillis(1000),
          now,
          Nil,
          Nil,
          MetricSnapshot.ofDistributions(
            "test.histogram",
            "test",
            Metric.Settings.ForDistributionInstrument(
              MeasurementUnit.none,
              java.time.Duration.ZERO,
              DynamicRange.Default
            ),
            Instrument.Snapshot(
              TagSet.from(Map("tag1" -> "value1")),
              buildHistogramDist(Seq(1L -> 2L, 2L -> 2L, 3L -> 3L, 5L -> 1L, 15L -> 1L))
            ) :: Nil
          ) :: Nil,
          Nil,
          Nil
        )
      )
      // basic sanity
      mockService.exportMetricsServiceRequest should not be empty
      mockService.exportMetricsServiceRequest.get should have size 1
      val exportedMetrics: Seq[MetricData] = mockService.exportMetricsServiceRequest.get.asScala.toSeq
      exportedMetrics should have size 1
      val metricData = exportedMetrics.head

      // check value
      metricData.getName should equal("test.histogram")
      metricData.getDescription should equal("test")
      val sumData = ExponentialHistogramData.fromMetricData(metricData)
      val points = sumData.getPoints.asScala.toSeq
      points should have size 1
      points.head.getAttributes should have size 1
      points.head.getAttributes.get(AttributeKey.stringKey("tag1")) should equal("value1")
      points.head.getScale shouldEqual 5
      points.head.getNegativeBuckets.getTotalCount shouldEqual 0L
      points.head.getZeroCount shouldEqual 2L
      points.head.getPositiveBuckets.getTotalCount shouldEqual 7L
      points.head.getSum shouldEqual 35L
      points.head.getCount shouldEqual 9L
    }

    "calculate sensible scales for values" in {
      def randomDouble = Random.nextInt(10) match {
        case 0 => 0d
        case 1 => Random.nextDouble() * 1e-18
        case 2 => Random.nextDouble() * 1e-12
        case 3 => Random.nextDouble() * 1e-6
        case 4 => Random.nextDouble() * 1e-3
        case 5 => Random.nextDouble()
        case 6 => Random.nextDouble() * 1e3
        case 7 => Random.nextDouble() * 1e6
        case 8 => Random.nextDouble() * 1e12
        case 9 => Random.nextDouble() * 1e18
      }

      for (i <- (0 to 100).map(_ => randomDouble); maxBucketCount <- (0 to 10).map(_ => Random.nextInt(320))) {
        val scale = MetricsConverter.maxScale(maxBucketCount)(i)
        val baseFromScale = Math.pow(2, Math.pow(2, -scale))
        val baseFromScale_plus_1 = Math.pow(2, Math.pow(2, -scale - 1))
        val maxFromBase = Math.pow(baseFromScale, maxBucketCount)
        val minFromBase = 1d / Math.pow(baseFromScale, maxBucketCount)
        val maxFromBase_plus_1 = Math.pow(baseFromScale_plus_1, maxBucketCount)
        val minFromBase_plus_1 = 1d / Math.pow(baseFromScale_plus_1, maxBucketCount)
        if (i >= 1) {
          if (scale != -10) maxFromBase should be >= i
          if (scale != 20) maxFromBase_plus_1 should be <= i
        } else {
          if (scale != -10) minFromBase should be <= i
          if (scale != 20) minFromBase_plus_1 should be >= i
        }
      }
    }
  }

  private def buildHistogramDist(_buckets: Seq[(Long, Long)]): Distribution = {

    val distribution: Distribution = new Distribution() {
      override def dynamicRange: DynamicRange = DynamicRange.Default

      override def min: Long = _buckets.minBy(_._1)._1

      override def max: Long = _buckets.maxBy(_._1)._1

      override def sum: Long = _buckets.foldLeft(0L) { case (a, (v, f)) => a + (v * f) }

      override def count: Long = _buckets.foldLeft(0L) { case (a, (_, f)) => a + f }

      override def percentile(rank: Double): Distribution.Percentile =
        ??? // percentileValues.get(rank).map(r => r.toPercentile).orNull

      override def percentiles: Seq[Distribution.Percentile] = ??? // Seq(perc.toPercentile)

      override def percentilesIterator: Iterator[Distribution.Percentile] = null

      override def buckets: Seq[Distribution.Bucket] = bucketsIterator.toSeq

      override def bucketsIterator: Iterator[Distribution.Bucket] = _buckets.iterator.map { case (v, f) =>
        PureDistributionBucket(v, f)
      }
    }
    distribution
  }

  case class PureDistributionBucket(value: Long, frequency: Long) extends Distribution.Bucket

  private class MockMetricsService extends MetricsService {
    var exportMetricsServiceRequest: Option[JCollection[MetricData]] = None
    var hasBeenClosed = false

    override def exportMetrics(metrics: JCollection[MetricData]): Future[Unit] = {
      exportMetricsServiceRequest = Option(metrics)
      Future.successful(())
    }

    override def close(): Unit = hasBeenClosed = true
  }
}
