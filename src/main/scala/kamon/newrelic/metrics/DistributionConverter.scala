package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.{Gauge, Metric, Summary}
import kamon.metric.Distribution.Bucket
import kamon.metric.MetricSnapshot.Distributions
import kamon.metric.{Distribution, DynamicRange, MeasurementUnit, MetricSnapshot}
import kamon.newrelic.TagSetToAttributes
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

object DistributionConverter {
  private val logger = LoggerFactory.getLogger(getClass)

  def convert(start: Long, end: Long, dist: MetricSnapshot.Distributions): Seq[Metric] = {
    logger.debug("name: {} ; numberOfInstruments: {}", dist.name, dist.instruments.size)

    val baseAttributes = buildBaseAttributes(dist)

    dist.instruments.flatMap { inst  =>
      val tags: TagSet = inst.tags
      val instrumentBaseAttributes: Attributes = TagSetToAttributes.addTags(Seq(tags), baseAttributes.copy())

      val distValue: Distribution = inst.value

      // Skip buckets - do not implement for now
      val buckets: Seq[Bucket] = distValue.buckets

      val summary: Summary = buildSummary(start, end, dist, instrumentBaseAttributes, distValue)
      val percentiles: scala.Seq[_root_.com.newrelic.telemetry.metrics.Metric] = makePercentiles(dist.name, end, distValue, instrumentBaseAttributes)
      percentiles.appended(summary)
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
    val kamonPercentiles: Seq[Distribution.Percentile] = distValue.percentiles
    kamonPercentiles.map { percentile =>
      val attributes: Attributes = instrumentBaseAttributes.copy()
        .put("percentile.countAtRank", percentile.countAtRank)
        .put("percentile", percentile.rank)
      new Gauge(name + ".percentiles", percentile.value, end, attributes)
    }
  }

  private def buildBaseAttributes(dist: Distributions): Attributes = {
    val dynamicRange: DynamicRange = dist.settings.dynamicRange
    val lowestDiscernibleValue = dynamicRange.lowestDiscernibleValue
    val highestTrackableValue = dynamicRange.highestTrackableValue
    val significantValueDigits = dynamicRange.significantValueDigits

    val unit: MeasurementUnit = dist.settings.unit
    val magnitude: MeasurementUnit.Magnitude = unit.magnitude
    val dimension: MeasurementUnit.Dimension = unit.dimension

    new Attributes()
      .put("sourceMetricType", "histogram")
      .put("lowestDiscernibleValue", lowestDiscernibleValue)
      .put("highestTrackableValue", highestTrackableValue)
      .put("significantValueDigits", significantValueDigits)
      .put("magnitude.name", magnitude.name)
      .put("magnitude.scaleFactor", magnitude.scaleFactor)
      .put("dimension", dimension.name)
  }
}
