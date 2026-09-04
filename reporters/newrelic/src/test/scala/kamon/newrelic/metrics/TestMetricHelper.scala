/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import java.time.{Duration, Instant}

import kamon.metric
import kamon.metric.Instrument.Snapshot
import kamon.metric.MeasurementUnit.Dimension
import kamon.metric.{Distribution, _}
import kamon.tag.TagSet

import scala.collection.{immutable, mutable}

object TestMetricHelper {

  val end: Long = System.currentTimeMillis()
  val endInstant: Instant = Instant.ofEpochMilli(end)
  val start: Long = end - 101
  val startInstant: Instant = Instant.ofEpochMilli(start)
  val value1: Long = 55L
  val value2: Long = 66L

  def buildCounter = {
    val tagSet: TagSet = TagSet.from(Map("foo" -> "bar"))
    val settings = Metric.Settings.ForValueInstrument(MeasurementUnit.percentage, Duration.ofMillis(12))
    val instrument1 = new Instrument.Snapshot[Long](tagSet, value1)
    val instrument2 = new Instrument.Snapshot[Long](tagSet, value2)
    MetricSnapshot.ofValues("flib", "flam", settings, Seq(instrument1, instrument2))
  }

  def buildGauge = {
    val tagSet: TagSet = TagSet.from(Map("foo" -> "bar"))
    val settings = Metric.Settings.ForValueInstrument(
      new MeasurementUnit(Dimension.Information, new MeasurementUnit.Magnitude("finch", 11.0d)),
      Duration.ofMillis(12)
    )
    val inst = new Instrument.Snapshot[Double](tagSet, 15.6d)
    new MetricSnapshot.Values[Double]("shirley", "another one", settings, Seq(inst))
  }

  def buildHistogramDistribution = {
    val tagSet: TagSet = TagSet.from(Map("twelve" -> "bishop"))
    val dynamicRange: DynamicRange = DynamicRange.Default
    val settings = Metric.Settings.ForDistributionInstrument(
      new MeasurementUnit(Dimension.Information, metric.MeasurementUnit.Magnitude("eimer", 603.3d)),
      Duration.ofMillis(12),
      dynamicRange
    )

    val percentiles = Map(90d -> Percentage(90d, 2L, 816L), 87d -> Percentage(87d, 2L, 816L))
    val distribution: Distribution =
      buildHistogramDist(Percentage(19d, 2L, 816L), Bucket(717L, 881L), Distro(13L, 17L, 101L, 44L), percentiles)
    val inst: Snapshot[Distribution] = new Snapshot[Distribution](tagSet, distribution)
    new metric.MetricSnapshot.Distributions("trev", "a good trevor", settings, Seq(inst))
  }

  def buildTimerDistribution = {
    val tagSet: TagSet = TagSet.from(Map("thirteen" -> "queen"))
    val dynamicRange: DynamicRange = DynamicRange.Default
    val settings = Metric.Settings.ForDistributionInstrument(
      new MeasurementUnit(Dimension.Information, new metric.MeasurementUnit.Magnitude("timer", 333.3d)),
      Duration.ofMillis(15),
      dynamicRange
    )
    val percentiles = Map(95d -> Percentage(95d, 4L, 1632L), 87d -> Percentage(87d, 2L, 816L))
    val distribution: Distribution =
      buildHistogramDist(Percentage(38d, 4L, 1632L), Bucket(1424L, 1672L), Distro(26L, 34L, 202L, 88L), percentiles)
    val inst: Snapshot[Distribution] = new Snapshot[Distribution](tagSet, distribution)
    new metric.MetricSnapshot.Distributions("timer", "a good timer", settings, Seq(inst))
  }

  def buildRangeSamplerDistribution = {
    val tagSet: TagSet = TagSet.from(Map("eleven" -> "elevenses"))
    val dynamicRange: DynamicRange = DynamicRange.Default
    val settings = Metric.Settings.ForDistributionInstrument(
      new MeasurementUnit(Dimension.Information, new metric.MeasurementUnit.Magnitude("home", 333.3d)),
      Duration.ofMillis(15),
      dynamicRange
    )
    val percentiles = Map(95d -> Percentage(95d, 8L, 1632L), 87d -> Percentage(87d, 4L, 816L))
    val distribution: Distribution =
      buildHistogramDist(Percentage(38d, 4L, 1632L), Bucket(1424L, 1672L), Distro(26L, 34L, 202L, 88L), percentiles)
    val inst: Snapshot[Distribution] = new Snapshot[Distribution](tagSet, distribution)
    new metric.MetricSnapshot.Distributions("ranger", "baby's first range sampler", settings, Seq(inst))
  }

  case class Percentage(r: Double, v: Long, c: Long) {
    def toPercentile: Distribution.Percentile = {
      new Distribution.Percentile {
        override def rank: Double = r

        override def value: Long = v

        override def countAtRank: Long = c
      }
    }
  }

  case class Bucket(v: Long, f: Long) {
    def toBucket: Distribution.Bucket = {
      new Distribution.Bucket {
        override def value: Long = v

        override def frequency: Long = f
      }
    }
  }

  case class Distro(min: Long, max: Long, sum: Long, count: Long)

  private def buildHistogramDist(
    perc: Percentage,
    bucket: Bucket,
    distro: Distro,
    percentileValues: Map[Double, Percentage]
  ) = {

    val distribution: Distribution = new Distribution() {
      override def dynamicRange: DynamicRange = DynamicRange.Default

      override def min: Long = distro.min

      override def max: Long = distro.max

      override def sum: Long = distro.sum

      override def count: Long = distro.count

      override def percentile(rank: Double): Distribution.Percentile =
        percentileValues.get(rank).map(r => r.toPercentile).orNull

      override def percentiles: Seq[Distribution.Percentile] = Seq(perc.toPercentile)

      override def percentilesIterator: Iterator[Distribution.Percentile] = null

      override def buckets: Seq[Distribution.Bucket] = Seq(bucket.toBucket)

      override def bucketsIterator: Iterator[Distribution.Bucket] = null
    }
    distribution
  }
}
