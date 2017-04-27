package kamon.metric.instrument

import kamon.metric.Entity
import kamon.util.MeasurementUnit

trait Gauge {
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement(): Unit
  def decrement(times: Long): Unit
  def set(value: Long): Unit
}

object Gauge {
  def apply(entity: Entity, name: String): Gauge = ???
}
