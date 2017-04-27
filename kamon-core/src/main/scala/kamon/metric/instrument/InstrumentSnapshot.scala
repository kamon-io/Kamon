package kamon.metric.instrument

import kamon.util.MeasurementUnit


case class SingleValueSnapshot(name: String, measurementUnit: MeasurementUnit, value: Long)

case class DistributionSnapshot(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange, distribution: Distribution)

trait DistributionSnapshotInstrument {
  def snapshot(): DistributionSnapshot
}

trait SingleValueSnapshotInstrument {
  def snapshot(): SingleValueSnapshot
}




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
