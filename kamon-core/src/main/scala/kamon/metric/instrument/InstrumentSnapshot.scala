package kamon.metric.instrument

import kamon.util.MeasurementUnit

/**
  * Snapshot for instruments that internally track a single value. Meant to be used for counters and gauges.
  *
  */
case class SingleValueSnapshot(name: String, tags: Map[String, String], measurementUnit: MeasurementUnit, value: Long)

/**
  * Snapshot for instruments that internally the distribution of values in a defined dynamic range. Meant to be used
  * with histograms and min max counters.
  */
case class DistributionSnapshot(name: String, tags: Map[String, String],  measurementUnit: MeasurementUnit,
  dynamicRange: DynamicRange, distribution: Distribution)


trait Distribution {
  def buckets: Seq[Bucket]
  def bucketsIterator: Iterator[Bucket]

  def min: Long
  def max: Long
  def sum: Long
  def count: Long
  def percentile(p: Double): Percentile

  def percentiles: Seq[Percentile]
  def percentilesIterator: Iterator[Percentile]
}

trait Bucket {
  def value: Long
  def frequency: Long
}

trait Percentile {
  def quantile: Double
  def value: Long
  def countUnderQuantile: Long
}


trait DistributionSnapshotInstrument {
  private[kamon] def snapshot(): DistributionSnapshot
}

trait SingleValueSnapshotInstrument {
  private[kamon] def snapshot(): SingleValueSnapshot
}

trait SnapshotableHistogram extends Histogram with DistributionSnapshotInstrument
trait SnapshotableMinMaxCounter extends MinMaxCounter with DistributionSnapshotInstrument
trait SnapshotableCounter extends Counter with SingleValueSnapshotInstrument
trait SnapshotableGauge extends Gauge with SingleValueSnapshotInstrument

