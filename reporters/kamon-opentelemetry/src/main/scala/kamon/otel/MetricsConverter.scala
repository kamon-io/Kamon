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

import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data._
import io.opentelemetry.sdk.metrics.internal.data._
import io.opentelemetry.sdk.metrics.internal.data.exponentialhistogram.{
  ExponentialHistogramBuckets,
  ExponentialHistogramData,
  ExponentialHistogramPointData,
  ImmutableExponentialHistogramData
}
import io.opentelemetry.sdk.resources.Resource
import kamon.metric.Instrument.Snapshot
import kamon.metric.{Distribution, MeasurementUnit, MetricSnapshot, PeriodSnapshot}
import kamon.otel.HistogramFormat.{Explicit, Exponential, HistogramFormat}
import kamon.otel.MetricsConverter.{ExplBucketFn, ExpoBucketFn}
import org.slf4j.LoggerFactory

import java.lang.{Double => JDouble, Long => JLong}
import java.time.Instant
import java.util
import java.util.{ArrayList => JArrayList, Collection => JCollection}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class WithResourceMetricsConverter(
  resource: Resource,
  kamonVersion: String,
  from: Instant,
  to: Instant,
  explBucketConfig: ExplBucketFn,
  expoBucketConfig: ExpoBucketFn
) {
  private val maxDouble: JDouble = JDouble.valueOf(JDouble.MAX_VALUE)
  private val logger = LoggerFactory.getLogger(getClass)
  private val fromNs = from.toEpochMilli * 1000000
  private val toNs = to.toEpochMilli * 1000000

  private def instrumentationScopeInfo(snapshot: MetricSnapshot[_, _]): InstrumentationScopeInfo =
    InstrumentationScopeInfo.create("kamon-metrics", kamonVersion, null)

  private def toString(unit: MeasurementUnit): String = unit.magnitude.name

  private def toGaugeDatum(g: Snapshot[Double]): DoublePointData =
    ImmutableDoublePointData.create(fromNs, toNs, SpanConverter.toAttributes(g.tags), g.value)

  private def toGaugeData(g: Seq[Snapshot[Double]]): GaugeData[DoublePointData] =
    ImmutableGaugeData.create(g.map(toGaugeDatum).asJava)

  def convertGauge(gauge: MetricSnapshot.Values[Double]): MetricData =
    ImmutableMetricData.createDoubleGauge(
      resource,
      instrumentationScopeInfo(gauge),
      gauge.name,
      gauge.description,
      toString(gauge.settings.unit),
      toGaugeData(gauge.instruments)
    )

  private def getExplBucketCounts(bucketConfiguration: Seq[JDouble])(s: Snapshot[Distribution]) = {
    val counts = ArrayBuffer.newBuilder[JLong]
    val boundaryIterator: Iterator[JDouble] = (bucketConfiguration :+ maxDouble).iterator
    var nextBoundary = boundaryIterator.next()
    var inBucketCount = 0L
    for (el <- s.value.bucketsIterator) {
      while (el.value > nextBoundary) {
        nextBoundary = boundaryIterator.next()
        counts += inBucketCount
        inBucketCount = 0L
      }
      inBucketCount += el.frequency
    }
    while (boundaryIterator.hasNext) {
      counts += inBucketCount
      boundaryIterator.next()
      inBucketCount = 0L
    }
    counts += inBucketCount
    counts
  }

  private def toExplicitHistogramDatum(bucketConfiguration: Seq[JDouble])(s: Snapshot[Distribution])
    : HistogramPointData = {
    val counts = getExplBucketCounts(bucketConfiguration)(s)
    ImmutableHistogramPointData.create(
      fromNs,
      toNs,
      SpanConverter.toAttributes(s.tags),
      JDouble valueOf s.value.sum.toDouble,
      JDouble valueOf s.value.min.toDouble,
      JDouble valueOf s.value.max.toDouble,
      bucketConfiguration.asJava,
      counts.result().asJava
    )
  }

  private def toExplicitHistogramData(
    bucketConfiguration: Seq[JDouble],
    distributions: Seq[Snapshot[Distribution]]
  ): Option[HistogramData] =
    distributions.filter(_.value.buckets.nonEmpty) match {
      case Nil => None
      case nonEmpty => Some(ImmutableHistogramData.create(
          AggregationTemporality.DELTA,
          nonEmpty.map(toExplicitHistogramDatum(bucketConfiguration)).asJava
        ))
    }

  def convertExplicitHistogram(histogram: MetricSnapshot.Distributions): Option[MetricData] = {
    val bucketConfiguration = explBucketConfig(histogram.name, histogram.settings.unit)
    toExplicitHistogramData(bucketConfiguration, histogram.instruments).map(d =>
      ImmutableMetricData.createDoubleHistogram(
        resource,
        instrumentationScopeInfo(histogram),
        histogram.name,
        histogram.description,
        toString(histogram.settings.unit),
        d
      )
    )
  }

  class ItWithLast[T](it: Iterator[T], last: T) extends Iterator[T] {
    private var showedLast: Boolean = false

    def hasNext: Boolean = it.hasNext || !showedLast

    def next(): T = if (it.hasNext) it.next()
    else if (!showedLast) {
      showedLast = true
      last
    } else throw new RuntimeException("Next on empty Iterator")
  }

  private def getExpoBucketCounts(scale: Int, maxBucketCount: Int)(s: Snapshot[Distribution]) = {
    val base = Math.pow(2, Math.pow(2, -scale))
    val lowerBoundaryIterator: Iterator[Double] =
      ((-maxBucketCount to maxBucketCount).map(i => Math.pow(base, i)) :+ Double.MaxValue).iterator
    val valuesIterator = new ItWithLast[Distribution.Bucket](
      s.value.bucketsIterator,
      new Distribution.Bucket {
        def value: Long = Long.MaxValue

        def frequency: Long = 0
      }
    )
    var fromLowerBound = valuesIterator.next()
    var fromUpperBound = valuesIterator.next()
    var toLowerBound = lowerBoundaryIterator.next()
    var toUpperBound = lowerBoundaryIterator.next()
    var zeroCount: JLong = 0L
    var countInBucket = 0L

    val negativeCounts = ArrayBuffer.newBuilder[JLong]
    val positiveCounts = ArrayBuffer.newBuilder[JLong]

    def iterFrom: JLong = {
      val d = fromLowerBound.frequency
      fromLowerBound = fromUpperBound
      fromUpperBound = valuesIterator.next()
      d
    }

    def iterTo: JLong = {
      toLowerBound = toUpperBound
      toUpperBound = lowerBoundaryIterator.next()
      val res = countInBucket
      countInBucket = 0
      res
    }
    // normal case
    while (lowerBoundaryIterator.hasNext && valuesIterator.hasNext) {
      if (fromUpperBound.value <= toLowerBound) {
        countInBucket += iterFrom // Or drop?
      } else if (fromLowerBound.value >= toUpperBound) toLowerBound match {
        case 1          => zeroCount += iterTo
        case b if b < 1 => negativeCounts += iterTo
        case b if b > 1 => positiveCounts += iterTo
      }
      else if (fromUpperBound.value == toUpperBound) toLowerBound match {
        case 1 =>
          countInBucket += iterFrom
          zeroCount += iterTo
        case b if b < 1 =>
          countInBucket += iterFrom
          negativeCounts += iterTo
        case b if b > 1 =>
          countInBucket += iterFrom
          positiveCounts += iterTo
      }
      else if (fromUpperBound.value > toUpperBound) {
        val firstBonus: JLong = countInBucket
        var negBuckets = 0
        var zeroBuckets = 0
        var posBuckets = 0
        while (fromUpperBound.value > toUpperBound && lowerBoundaryIterator.hasNext) {
          if (toLowerBound < 1) negBuckets += 1
          else if (toLowerBound == 1) zeroBuckets += 1
          else if (toLowerBound >= 1) posBuckets += 1
          toLowerBound = toUpperBound
          toUpperBound = lowerBoundaryIterator.next()
        }
        val total = iterFrom
        // Not sure about this... everything's going into the first bucket, even though we might be spanning multiple target buckets.
        // Might be better to do something like push the avg.floor into each bucket, interpolating the remainder.
        // OTOH it may not really come up much in practice, since the internal histos are likely to have similar or finer granularity
        negativeCounts ++= (if (negBuckets > 0)
                              JLong.valueOf(firstBonus + total) +: Array.fill(negBuckets - 1)(JLong.valueOf(0))
                            else Nil)
        zeroCount += (if (negBuckets == 0 && zeroBuckets == 1) JLong.valueOf(firstBonus + total) else JLong.valueOf(0))
        positiveCounts ++= (
          if (negBuckets == 0 && zeroBuckets == 0 && posBuckets > 0)
            JLong.valueOf(firstBonus + total) +: Array.fill(posBuckets - 1)(JLong.valueOf(0))
          else Array.fill(posBuckets)(JLong.valueOf(0))
        )
      } else /*if (fromUpperBound.value < toUpperBound) */ toLowerBound match {
        case 1 => zeroCount += iterFrom
        case _ => countInBucket += iterFrom
      }
    }
    var usedLastValue = false
    // more buckets left to fill but only one unused value, sitting in fromLowerBound.
    while (lowerBoundaryIterator.hasNext) {
      if (fromLowerBound.value > toLowerBound && fromLowerBound.value < toUpperBound) {
        usedLastValue = true
        countInBucket += fromLowerBound.frequency
      }
      toLowerBound match {
        case 1          => zeroCount += iterTo
        case b if b < 1 => negativeCounts += iterTo
        case b if b > 1 => positiveCounts += iterTo
      }
    }
    // more values left, but only one unfilled bucket, sitting in toLowerBound
    while (valuesIterator.hasNext) {
      countInBucket += iterFrom
    }
    if (!usedLastValue) countInBucket += fromLowerBound.frequency
    positiveCounts += countInBucket

    val negBucket: ExponentialHistogramBuckets = new ExponentialHistogramBuckets {
      val getOffset: Int = -maxBucketCount
      private val longs: ArrayBuffer[JLong] = negativeCounts.result()
      val getBucketCounts: util.List[JLong] = new JArrayList(longs.asJava)
      val getTotalCount: Long = longs.foldLeft(0L)(_ + _)
    }
    val posBucket: ExponentialHistogramBuckets = new ExponentialHistogramBuckets {
      val getOffset: Int = 1
      private val longs: ArrayBuffer[JLong] = positiveCounts.result()
      val getBucketCounts: util.List[JLong] = new JArrayList(longs.asJava)
      val getTotalCount: Long = longs.foldLeft(0L)(_ + _)
    }
    (negBucket, zeroCount, posBucket)
  }

  private def toExponentialHistogramData(
    maxBucketCount: Int,
    distributions: Seq[Snapshot[Distribution]]
  ): Option[ExponentialHistogramData] =
    distributions.filter(_.value.buckets.nonEmpty) match {
      case Nil => None
      case nonEmpty =>
        val mapped = nonEmpty.flatMap { s =>
          def maxScale(v: JDouble): Int = MetricsConverter.maxScale(maxBucketCount)(v)

          // Could also calculate an 'offset' here, but defaulting to offset = 1 for simplicity
          val scale = Math.min(maxScale(s.value.min.toDouble), maxScale(s.value.max.toDouble))
          val (neg, zero, pos) = getExpoBucketCounts(scale, maxBucketCount)(s)
          Some(ExponentialHistogramPointData.create(
            scale,
            s.value.sum,
            zero,
            pos,
            neg,
            fromNs,
            toNs,
            SpanConverter.toAttributes(s.tags),
            new JArrayList[DoubleExemplarData]()
          ))
        }
        if (mapped.nonEmpty) Some(ImmutableExponentialHistogramData.create(AggregationTemporality.DELTA, mapped.asJava))
        else None
    }

  def convertExponentialHistogram(histogram: MetricSnapshot.Distributions): Option[MetricData] = {
    val maxBucketCount = expoBucketConfig(histogram.name, histogram.settings.unit)
    toExponentialHistogramData(maxBucketCount, histogram.instruments).map(d =>
      ImmutableMetricData.createExponentialHistogram(
        resource,
        instrumentationScopeInfo(histogram),
        histogram.name,
        histogram.description,
        toString(histogram.settings.unit),
        d
      )
    )
  }

  def convertHistogram(histogramFormat: HistogramFormat)(histogram: MetricSnapshot.Distributions): Option[MetricData] =
    histogramFormat match {
      case Explicit    => convertExplicitHistogram(histogram)
      case Exponential => convertExponentialHistogram(histogram)
    }

  private def toCounterDatum(g: Snapshot[Long]): LongPointData =
    ImmutableLongPointData.create(fromNs, toNs, SpanConverter.toAttributes(g.tags), g.value)

  private def toCounterData(g: Seq[Snapshot[Long]]): SumData[LongPointData] =
    ImmutableSumData.create(false, AggregationTemporality.DELTA, g.map(toCounterDatum).asJava)

  def convertCounter(counter: MetricSnapshot.Values[Long]): MetricData =
    ImmutableMetricData.createLongSum(
      resource,
      instrumentationScopeInfo(counter),
      counter.name,
      counter.description,
      toString(counter.settings.unit),
      toCounterData(counter.instruments)
    )

}

/**
 * Converts Kamon metrics to OpenTelemetry [[MetricData]]s
 */
private[otel] object MetricsConverter {
  type ExplBucketFn = (String, MeasurementUnit) => Seq[JDouble]
  type ExpoBucketFn = (String, MeasurementUnit) => Int
  private val minScale = -10
  private val maxScale = 20

  def convert(
    resource: Resource,
    kamonVersion: String,
    histogramFormat: HistogramFormat,
    explicitBucketConfig: ExplBucketFn,
    exponentialBucketConfig: ExpoBucketFn
  )(metrics: PeriodSnapshot): JCollection[MetricData] = {
    val converter = new WithResourceMetricsConverter(
      resource,
      kamonVersion,
      metrics.from,
      metrics.to,
      explicitBucketConfig,
      exponentialBucketConfig
    )
    val gauges = metrics.gauges.filter(_.instruments.nonEmpty).map(converter.convertGauge)
    val histograms = (metrics.histograms ++ metrics.timers ++ metrics.rangeSamplers).filter(_.instruments.nonEmpty)
      .flatMap(converter.convertHistogram(histogramFormat))
    val counters = metrics.counters.filter(_.instruments.nonEmpty).map(converter.convertCounter)

    (gauges ++ histograms ++ counters).asJava
  }

  private val bases = (maxScale to minScale by -1).map(scale => (scale, Math.pow(2, Math.pow(2, -scale)))).toArray

  def maxScale(maxBucketCount: Int)(v: JDouble): Int = {
    if (v >= 1)
      bases.collectFirst { case (scale, base) if Math.pow(base, maxBucketCount) >= v => scale }.getOrElse(minScale)
    else bases.collectFirst { case (scale, base) if Math.pow(base, -maxBucketCount) <= v => scale }.getOrElse(minScale)
  }
}
