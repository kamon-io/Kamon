package kamon.metric.snapshot

import kamon.util.MeasurementUnit

sealed trait InstrumentSnapshot {
  def name: String
  def measurementUnit: MeasurementUnit
}

trait DistributionSnapshot extends InstrumentSnapshot {
  def distribution: Distribution
}

trait SingleValueSnapshot extends InstrumentSnapshot {
  def value: Long
}