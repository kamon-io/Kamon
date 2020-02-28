/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.{Gauge, Metric, Summary}
import kamon.metric.Distribution.Bucket
import kamon.metric.MetricSnapshot.Distributions
import kamon.metric.{Distribution, DynamicRange, MeasurementUnit, MetricSnapshot}
import kamon.newrelic.AttributeBuddy._
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

object NewRelicDistributionMetrics {
  private val percentilesToReport = Seq(50d, 75d, 90d, 95d, 99d, 99.9d)
  private val logger = LoggerFactory.getLogger(getClass)

  def apply(start: Long, end: Long, dist: MetricSnapshot.Distributions, sourceMetricType: String): Seq[Metric] = {
    logger.debug("name: {} ; numberOfInstruments: {}", dist.name, dist.instruments.size)

    val baseAttributes = buildBaseAttributes(dist, sourceMetricType)

    dist.instruments.flatMap { inst =>
      val tags: TagSet = inst.tags
      val instrumentBaseAttributes: Attributes = addTagsFromTagSets(Seq(tags), baseAttributes.copy())

      val distValue: Distribution = inst.value

      // Skip buckets - do not implement for now
      val buckets: Seq[Bucket] = distValue.buckets

      val summary: Summary = buildSummary(start, end, dist, instrumentBaseAttributes, distValue)
      val percentiles: scala.Seq[_root_.com.newrelic.telemetry.metrics.Metric] = makePercentiles(dist.name, end, distValue, instrumentBaseAttributes)
      percentiles :+ summary
    }
  }

  private def buildSummary(start: Long, end: Long, dist: Distributions, instrumentBaseAttributes: Attributes, distValue: Distribution) = {
    val count: Long = distValue.count
    val sum: Long = distValue.sum
    val min: Long = distValue.min
    val max: Long = distValue.max
    new Summary(dist.name + ".summary", count.toInt, sum, min, max, start, end, instrumentBaseAttributes)
  }

  private def makePercentiles(name: String, end: Long, distValue: Distribution, instrumentBaseAttributes: Attributes): Seq[Metric] = {
    percentilesToReport
      .map(rank => distValue.percentile(rank))
      .filter(percentileValue => percentileValue != null)
      .map { percentile =>
        val attributes: Attributes = instrumentBaseAttributes.copy()
          .put("percentile", percentile.rank)
        new Gauge(name + ".percentiles", percentile.value, end, attributes)
      }
  }

  private def buildBaseAttributes(dist: Distributions, sourceMetricType: String): Attributes = {
    val dynamicRange: DynamicRange = dist.settings.dynamicRange
    val lowestDiscernibleValue = dynamicRange.lowestDiscernibleValue
    val highestTrackableValue = dynamicRange.highestTrackableValue
    val significantValueDigits = dynamicRange.significantValueDigits

    val unit: MeasurementUnit = dist.settings.unit
    val magnitude: MeasurementUnit.Magnitude = unit.magnitude
    val dimension: MeasurementUnit.Dimension = unit.dimension

    new Attributes()
      .put("sourceMetricType", sourceMetricType)
      .put("lowestDiscernibleValue", lowestDiscernibleValue)
      .put("highestTrackableValue", highestTrackableValue)
      .put("significantValueDigits", significantValueDigits)
      .put("magnitude.name", magnitude.name)
      .put("magnitude.scaleFactor", magnitude.scaleFactor)
      .put("dimension", dimension.name)
  }
}
