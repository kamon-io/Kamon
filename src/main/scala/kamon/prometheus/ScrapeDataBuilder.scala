package kamon.prometheus

import java.lang.StringBuilder
import java.text.DecimalFormat

import kamon.metric.{MetricDistribution, MetricValue}
import kamon.util.MeasurementUnit
import kamon.util.MeasurementUnit.{information, time, none}
import kamon.util.MeasurementUnit.Dimension._

class ScrapeDataBuilder(prometheusConfig: PrometheusReporter.Configuration) {
  private val builder = new StringBuilder()
  private val numberFormat = new DecimalFormat("#0.0########")

  import builder.append

  def build(): String =
    builder.toString()

  def appendCounters(counters: Seq[MetricValue]): ScrapeDataBuilder = {
    counters.groupBy(_.name).foreach(appendValueMetric("counter"))
    this
  }

  def appendGauges(gauges: Seq[MetricValue]): ScrapeDataBuilder = {
    gauges.groupBy(_.name).foreach(appendValueMetric("gauge"))
    this
  }

  def appendHistograms(histograms: Seq[MetricDistribution]): ScrapeDataBuilder = {
    histograms.groupBy(_.name).foreach(appendDistributionMetric)
    this
  }



  private def appendValueMetric(metricType: String)(group: (String, Seq[MetricValue])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val normalizedMetricName = normalizeMetricName(metricName, unit)

    append("# TYPE ").append(normalizedMetricName).append(" ").append(metricType).append("\n")

    snapshots.foreach(metric => {
      append(normalizedMetricName)
      appendTags(metric.tags)
      append(" ")
      append(format(scale(metric.value, metric.unit)))
      append("\n")
    })
  }

  private def appendDistributionMetric(group: (String, Seq[MetricDistribution])): Unit = {
    val (metricName, snapshots) = group
    val unit = snapshots.headOption.map(_.unit).getOrElse(none)
    val normalizedMetricName = normalizeMetricName(metricName, unit)

    append("# TYPE ").append(normalizedMetricName).append(" histogram").append("\n")

    snapshots.foreach(metric => {
      if(metric.distribution.count > 0) {
        appendHistogramBuckets(normalizedMetricName, metric.tags, metric)

        val count = format(metric.distribution.count)
        val sum = format(scale(metric.distribution.sum, metric.unit))
        appendTimeSerieValue(normalizedMetricName, metric.tags, count, "_count")
        appendTimeSerieValue(normalizedMetricName, metric.tags, sum, "_sum")
      }
    })
  }

  private def appendTimeSerieValue(name: String, tags: Map[String, String], value: String, suffix: String = ""): Unit = {
    append(name)
    append(suffix)
    appendTags(tags)
    append(" ")
    append(value)
    append("\n")
  }

  private def appendHistogramBuckets(name: String, tags: Map[String, String], metric: MetricDistribution): Unit = {
    val configuredBuckets = (metric.unit.dimension match {
      case Time         => prometheusConfig.timeBuckets
      case Information  => prometheusConfig.informationBuckets
      case _            => prometheusConfig.defaultBuckets
    }).iterator

    val distributionBuckets = metric.distribution.bucketsIterator
    var currentDistributionBucket = distributionBuckets.next()
    var currentDistributionBucketValue = scale(currentDistributionBucket.value, metric.unit)
    var inBucketCount = 0L
    var leftOver = currentDistributionBucket.frequency

    configuredBuckets.foreach { configuredBucket =>
      val bucketTags = tags + ("le" -> String.valueOf(configuredBucket))

      if(currentDistributionBucketValue <= configuredBucket) {
        inBucketCount += leftOver
        leftOver = 0

        while (distributionBuckets.hasNext && currentDistributionBucketValue <= configuredBucket ) {
          currentDistributionBucket = distributionBuckets.next()
          currentDistributionBucketValue = scale(currentDistributionBucket.value, metric.unit)

          if (currentDistributionBucketValue <= configuredBucket) {
            inBucketCount += currentDistributionBucket.frequency
          }
          else
            leftOver = currentDistributionBucket.frequency
        }
      }

      appendTimeSerieValue(name, bucketTags, format(inBucketCount), "_bucket")
    }

    while(distributionBuckets.hasNext) {
      leftOver += distributionBuckets.next().frequency
    }

    appendTimeSerieValue(name, tags + ("le" -> "+Inf"), format(leftOver + inBucketCount), "_bucket")
  }



  private def appendTags(tags: Map[String, String]): Unit = {
    if(tags.nonEmpty) append("{")

    val tagIterator = tags.iterator
    var tagCount = 0

    while(tagIterator.hasNext) {
      val (key, value) = tagIterator.next()
      if(tagCount > 0) append(",")
      append(key).append("=\"").append(value).append('"')
      tagCount += 1
    }

    if(tags.nonEmpty) append("}")
  }

  private def normalizeMetricName(metricName: String, unit: MeasurementUnit): String = {
    def charOrUnderscore(char: Char): Char = if(char.isLetterOrDigit || char == '_') char else '_'
    val formattedMetricName = metricName.map(charOrUnderscore)

    unit.dimension match  {
      case Time         => formattedMetricName + "_seconds"
      case Information  => formattedMetricName + "_bytes"
      case _            => formattedMetricName
    }
  }

  private def format(value: Double): String =
    numberFormat.format(value)

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds       => MeasurementUnit.scale(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes  => MeasurementUnit.scale(value, unit, information.bytes)
    case _ => value
  }


}
