package kamon.testkit

import java.time.Duration

import kamon.metric.Histogram
import kamon.metric.Metric.Settings
import kamon.metric.{DynamicRange, Instrument, MeasurementUnit, MetricSnapshot}
import kamon.tag.TagSet

/**
  * Utilities for creating metric snapshots for testing purposes.
  */
object MetricSnapshotBuilder {

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def counter(name: String, tags: TagSet, value: Long): MetricSnapshot.Values[Long] =
    counter(name, "", tags, MeasurementUnit.none, value)

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def counter(name: String, description: String, tags: TagSet, value: Long): MetricSnapshot.Values[Long] =
    counter(name, description, tags, MeasurementUnit.none, value)

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def counter(name: String, description: String, tags: TagSet, unit: MeasurementUnit, value: Long): MetricSnapshot.Values[Long] = {
    MetricSnapshot(
      name,
      description,
      Settings.ForValueInstrument(unit, Duration.ZERO),
      Seq(Instrument.Snapshot(tags, value))
    )
  }

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def gauge(name: String, tags: TagSet, value: Double): MetricSnapshot.Values[Double] =
    gauge(name, "", tags, MeasurementUnit.none, value)

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def gauge(name: String, description: String, tags: TagSet, value: Double): MetricSnapshot.Values[Double] =
    gauge(name, description, tags, MeasurementUnit.none, value)

  /**
    * Returns a metric snapshot containing a single instrument with a snapshot containing the provided attributes.
    */
  def gauge(name: String, description: String, tags: TagSet, unit: MeasurementUnit, value: Double): MetricSnapshot.Values[Double] = {
    MetricSnapshot(
      name,
      description,
      Settings.ForValueInstrument(unit, Duration.ZERO),
      Seq(Instrument.Snapshot(tags, value))
    )
  }

  /**
    * Returns a metric snapshot containing a single instrument with a distribution snapshot containing the provided
    * attributes and values.
    */
  def histogram(name: String, tags: TagSet)(values: Long*): MetricSnapshot.Distributions =
    histogram(name, "", tags, MeasurementUnit.none)(values: _*)

  /**
    * Returns a metric snapshot containing a single instrument with a distribution snapshot containing the provided
    * attributes and values.
    */
  def histogram(name: String, description: String, tags: TagSet)(values: Long*): MetricSnapshot.Distributions =
    histogram(name, description, tags, MeasurementUnit.none)(values: _*)

  /**
    * Returns a metric snapshot containing a single instrument with a distribution snapshot containing the provided
    * attributes and values.
    */
  def histogram(name: String, description: String, tags: TagSet, unit: MeasurementUnit)(values: Long*): MetricSnapshot.Distributions = {
    val localHistogram = Histogram.Local.get(DynamicRange.Default)
    localHistogram.reset()

    values.foreach(v => localHistogram.recordValue(v))

    MetricSnapshot(
      name,
      description,
      Settings.ForDistributionInstrument(unit, Duration.ZERO, DynamicRange.Default),
      Seq(Instrument.Snapshot(tags, localHistogram.snapshot(true)))
    )
  }

}
