package kamon.newrelic.metrics

import java.time.{Duration, Instant}

import kamon.metric
import kamon.metric.Instrument.Snapshot
import kamon.metric.MeasurementUnit.Dimension
import kamon.metric._
import kamon.tag.TagSet

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
      new MeasurementUnit(Dimension.Information, new MeasurementUnit.Magnitude("finch", 11.0d)), Duration.ofMillis(12))
    val inst = new Instrument.Snapshot[Double](tagSet, 15.6d)
    new MetricSnapshot.Values[Double]("shirley", "another one", settings, Seq(inst))
  }

  def buildDistribution = {
    val tagSet: TagSet = TagSet.from(Map("twelve" -> "bishop"))
    val dynamicRange: DynamicRange = DynamicRange.Default
    val settings = Metric.Settings.ForDistributionInstrument(
      new MeasurementUnit(Dimension.Information, new metric.MeasurementUnit.Magnitude("eimer", 603.3d)), Duration.ofMillis(12), dynamicRange)
    val distribution: Distribution = buildDist
    val inst: Snapshot[Distribution] = new Snapshot[Distribution](tagSet, distribution)
    new metric.MetricSnapshot.Distributions("trev", "a good trevor", settings, Seq(inst))
  }

  private def buildDist = {
    val perc = new Distribution.Percentile{
      override def rank: Double = 19

      override def value: Long = 2

      override def countAtRank: Long = 816
    }
    val bucket = new Distribution.Bucket {
      override def value: Long = 717
      override def frequency: Long = 881
    }
    val distribution: Distribution = new Distribution() {
      override def dynamicRange: DynamicRange = DynamicRange.Default

      override def min: Long = 13

      override def max: Long = 17

      override def sum: Long = 101

      override def count: Long = 44

      override def percentile(rank: Double): Distribution.Percentile = null

      override def percentiles: Seq[Distribution.Percentile] = Seq(perc)

      override def percentilesIterator: Iterator[Distribution.Percentile] = null

      override def buckets: Seq[Distribution.Bucket] = Seq(bucket)

      override def bucketsIterator: Iterator[Distribution.Bucket] = null
    }
    distribution
  }
}
