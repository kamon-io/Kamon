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

import java.lang.StringBuilder
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale
import kamon.metric.{Distribution, Instrument, MeasurementUnit, Metric, MetricSnapshot}
import kamon.metric.MeasurementUnit.{information, time}
import kamon.metric.MeasurementUnit.Dimension._
import kamon.tag.TagSet

class ScrapeDataBuilder(prometheusConfig: PrometheusSettings.Generic, environmentTags: TagSet = TagSet.Empty) {
  private val _builder = new StringBuilder()
  private val _decimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.ROOT)
  private val _numberFormat = new DecimalFormat("#0.0########", _decimalFormatSymbols)

  import _builder.append

  def build(): String =
    _builder.toString()

  def appendCounters(counters: Seq[MetricSnapshot.Values[Long]]): ScrapeDataBuilder = {
    counters.foreach(appendCounterMetric)
    this
  }

  def appendGauges(gauges: Seq[MetricSnapshot.Values[Double]]): ScrapeDataBuilder = {
    gauges.foreach(appendGaugeMetric)
    this
  }

  def appendHistograms(histograms: Seq[MetricSnapshot.Distributions]): ScrapeDataBuilder = {
    histograms.foreach(appendDistributionMetric)
    this
  }

  def appendDistributionMetricsAsGauges(distributions: Seq[MetricSnapshot.Distributions]): ScrapeDataBuilder = {
    def gaugeFilter(metric:MetricSnapshot.Distributions):Boolean = prometheusConfig.gaugeSettings.metricMatchers.exists(_.accept(metric.name))
    def avg(snap:Instrument.Snapshot[Distribution]):Double = if(snap.value.count == 0) 0 else snap.value.sum/snap.value.count

    distributions
      .filter(gaugeFilter)
      .map{ metric =>
        val settings = Metric.Settings.ForValueInstrument(metric.settings.unit, metric.settings.autoUpdateInterval)
        Seq(
          MetricSnapshot.ofValues(
            name = metric.name+".min",
            description = metric.description,
            settings = settings,
            instruments = metric.instruments.map(snap => Instrument.Snapshot[Double](snap.tags, snap.value.min.toDouble))
          ),
          MetricSnapshot.ofValues(
            name = metric.name+".max",
            description = metric.description,
            settings = settings,
            instruments = metric.instruments.map(snap => Instrument.Snapshot[Double](snap.tags, snap.value.max.toDouble))
          ),
          MetricSnapshot.ofValues(
            name = metric.name+".avg",
            description = metric.description,
            settings = settings,
            instruments = metric.instruments.map(snap => Instrument.Snapshot[Double](snap.tags, avg(snap)))
          )
        )
    }.flatten
     .foreach(appendGaugeMetric)
    this
  }

  private def appendCounterMetric(metric: MetricSnapshot.Values[Long]): Unit = {
    val unit = metric.settings.unit
    val normalizedMetricName = normalizeCounterMetricName(metric.name, unit)

    if(metric.description.nonEmpty)
      append("# HELP ").append(normalizedMetricName).append(" ").append(metric.description).append("\n")

    append("# TYPE ").append(normalizedMetricName).append(" counter\n")

    metric.instruments.foreach(instrument => {
      append(normalizedMetricName)
      appendTags(instrument.tags)
      append(" ")
      append(format(convert(instrument.value, unit)))
      append("\n")
    })
  }

  private def appendGaugeMetric(metric: MetricSnapshot.Values[Double]): Unit = {
    val unit = metric.settings.unit
    val normalizedMetricName = normalizeMetricName(metric.name, unit)

    if(metric.description.nonEmpty)
      append("# HELP ").append(normalizedMetricName).append(" ").append(metric.description).append("\n")

    append("# TYPE ").append(normalizedMetricName).append(" gauge\n")

    metric.instruments.foreach(instrument => {
      append(normalizedMetricName)
      appendTags(instrument.tags)
      append(" ")
      append(format(convert(instrument.value, unit)))
      append("\n")
    })
  }

  private def appendDistributionMetric(metric: MetricSnapshot.Distributions): Unit = {
    val reportAsSummary = prometheusConfig.summarySettings.metricMatchers.exists(_.accept(metric.name))
    if (reportAsSummary) {
        appendDistributionMetricAsSummary(metric)
    } else {
        appendDistributionMetricAsHistogram(metric)
    }
  }

  private def appendDistributionMetricAsHistogram(metric: MetricSnapshot.Distributions): Unit = {
    val unit = metric.settings.unit
    val normalizedMetricName = normalizeMetricName(metric.name, unit)

    if(metric.description.nonEmpty)
      append("# HELP ").append(normalizedMetricName).append(" ").append(metric.description).append("\n")

    append("# TYPE ").append(normalizedMetricName).append(" histogram").append("\n")

    metric.instruments.foreach(instrument => {
      if(instrument.value.count > 0) {
        appendHistogramBuckets(normalizedMetricName, instrument.tags, instrument.value, unit,
          resolveBucketConfiguration(metric.name, unit))

        val count = format(instrument.value.count)
        val sum = format(convert(instrument.value.sum, unit))
        appendTimeSerieValue(normalizedMetricName, instrument.tags, count, "_count")
        appendTimeSerieValue(normalizedMetricName, instrument.tags, sum, "_sum")
      }
    })
  }

  private def appendDistributionMetricAsSummary(metric: MetricSnapshot.Distributions): Unit = {
    val unit = metric.settings.unit
    val normalizedMetricName = normalizeMetricName(metric.name, unit)

    if(metric.description.nonEmpty)
      append("# HELP ").append(normalizedMetricName).append(" ").append(metric.description).append("\n")

    append("# TYPE ").append(normalizedMetricName).append(" summary").append("\n")

    metric.instruments.foreach(instrument => {
      if(instrument.value.count > 0) {
        appendSummaryQuantiles(
          normalizedMetricName,
          instrument.tags,
          instrument.value,
          unit,
          prometheusConfig.summarySettings.quantiles
        )
        appendTimeSerieValue(normalizedMetricName, instrument.tags, format(instrument.value.count), "_count")
        appendTimeSerieValue(normalizedMetricName, instrument.tags, format(instrument.value.sum), "_sum")
      }
    })

  }

  private def appendTimeSerieValue(name: String, tags: TagSet, value: String, suffix: String = ""): Unit = {
    append(name)
    append(suffix)
    appendTags(tags)
    append(" ")
    append(value)
    append("\n")
  }

  private def resolveBucketConfiguration(metricName: String, unit: MeasurementUnit): Seq[java.lang.Double] =
    prometheusConfig.customBuckets.getOrElse(
      metricName,
      unit.dimension match {
        case Time         => prometheusConfig.timeBuckets
        case Information  => prometheusConfig.informationBuckets
        case Percentage   => prometheusConfig.percentageBuckets
        case _            => prometheusConfig.defaultBuckets
      }
    )

  private def appendSummaryQuantiles(name: String,
                                     tags: TagSet,
                                     distribution: Distribution,
                                     unit: MeasurementUnit,
                                     quantiles: Seq[java.lang.Double]): Unit = {
    val percentileIter = distribution.percentilesIterator
    val percentiles = quantiles.sorted.map { quant =>
      //find first percentile in iterator that is grater or equal to wanted quantile and extract value right away since the percentile is mutable
      quant -> percentileIter.find {
        _.rank >= (quant * 100)
      }.map(_.value).getOrElse(0L)
    }
    // Add percentiles timeseries for each percentile
    percentiles.foreach { case (quantileRank, percentile) =>
      val percTags = tags.withTag("quantile", format(quantileRank))
      appendTimeSerieValue(name, percTags, format(convert(percentile.toDouble, unit)))
    }
  }

  private def appendHistogramBuckets(name: String, tags: TagSet, distribution: Distribution, unit: MeasurementUnit,
      buckets: Seq[java.lang.Double]): Unit = {

    val distributionBuckets = distribution.bucketsIterator
    var currentDistributionBucket = distributionBuckets.next()
    var currentDistributionBucketValue = convert(currentDistributionBucket.value, unit)
    var inBucketCount = 0L
    var leftOver = currentDistributionBucket.frequency

    buckets.foreach { configuredBucket =>
      val bucketTags = tags.withTag("le", String.valueOf(configuredBucket))

      if(currentDistributionBucketValue <= configuredBucket) {
        inBucketCount += leftOver
        leftOver = 0

        while (distributionBuckets.hasNext && currentDistributionBucketValue <= configuredBucket ) {
          currentDistributionBucket = distributionBuckets.next()
          currentDistributionBucketValue = convert(currentDistributionBucket.value, unit)

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

    appendTimeSerieValue(name, tags.withTag("le", "+Inf"), format(leftOver + inBucketCount), "_bucket")
  }

  private def appendTags(tags: TagSet): Unit =
    appendTagsTo(tags, _builder)

  private def stringifyTags(tags: TagSet): Unit = {
    val builder = new StringBuilder()
    appendTagsTo(tags, builder)
    builder.toString
  }

  private def appendTagsTo(tags: TagSet, buffer: StringBuilder): Unit = {
    val allTags = tags.withTags(environmentTags)
    if(allTags.nonEmpty) buffer.append("{")

    val tagIterator = allTags.iterator(v => if(v == null) "" else v.toString)
    var tagCount = 0

    while(tagIterator.hasNext) {
      val pair = tagIterator.next()
      if(tagCount > 0)
        buffer.append(",")

      buffer
        .append(normalizeLabelName(pair.key))
        .append("=\"")
        .append(normalizeLabelValue(pair.value))
        .append('"')

      tagCount += 1
    }

    if(allTags.nonEmpty) buffer.append("}")
  }

  private def normalizeCounterMetricName(metricName: String, unit: MeasurementUnit): String = {
    val normalizedMetricName = metricName.map(validNameChar(_))

    unit.dimension match  {
      case Time         => addPostfixOnlyIfMissing(normalizedMetricName, "_seconds_total")
      case Information  => addPostfixOnlyIfMissing(normalizedMetricName, "_bytes_total")
      case _            => addPostfixOnlyIfMissing(normalizedMetricName, "_total")
    }
  }
  private def normalizeMetricName(metricName: String, unit: MeasurementUnit): String = {
    val normalizedMetricName = metricName.map(validNameChar(_))

    unit.dimension match  {
      case Time         => addPostfixOnlyIfMissing(normalizedMetricName, "_seconds")
      case Information  => addPostfixOnlyIfMissing(normalizedMetricName, "_bytes")
      case _            => normalizedMetricName
    }
  }
  private def addPostfixOnlyIfMissing(metricName: String, postfix: String) =
    if (metricName.endsWith(postfix)) metricName
    else metricName + postfix

  private def normalizeLabelName(label: String): String =
    label.map(validLabelChar)

  private def validLabelChar(char: Char): Char =
    if(char.isLetterOrDigit || char == '_') char else '_'

  private def validNameChar(char: Char): Char =
    if(char.isLetterOrDigit || char == '_' || char == ':') char else '_'

  private def normalizeLabelValue(value: String): String = {
    if(value.contains("\\")) value.replace("\\", "\\\\") else value
  }

  private def format(value: Double): String =
    _numberFormat.format(value)

  private def convert(value: Double, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds.magnitude       => MeasurementUnit.convert(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes.magnitude  => MeasurementUnit.convert(value, unit, information.bytes)
    case _ => value
  }


}
