package kamon.metric.instrument

import java.util.concurrent.atomic.AtomicLong
import kamon.util.MeasurementUnit

trait Gauge {
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement(): Unit
  def decrement(times: Long): Unit
  def set(value: Long): Unit
}


class AtomicLongGauge(name: String, tags: Map[String, String], val measurementUnit: MeasurementUnit)
  extends SnapshotableGauge {

  private val currentValue = new AtomicLong(0L)

  def increment(): Unit =
    currentValue.incrementAndGet()

  def increment(times: Long): Unit =
    currentValue.addAndGet(times)

  def decrement(): Unit =
    currentValue.decrementAndGet()

  def decrement(times: Long): Unit =
    currentValue.addAndGet(-times)

  def set(value: Long): Unit =
    currentValue.set(value)

  def snapshot(): SingleValueSnapshot =
    SingleValueSnapshot(name, tags, measurementUnit, currentValue.get())
}
