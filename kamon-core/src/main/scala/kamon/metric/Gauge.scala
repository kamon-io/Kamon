package kamon.metric

import java.util.concurrent.atomic.AtomicLong

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet


/**
  * Instrument that tracks the latest observed value of a given measure.
  */
trait Gauge extends Instrument[Gauge, Metric.Settings.ValueInstrument] {

  /**
    * Increments the current value of the gauge by one unit.
    */
  def increment(): Gauge

  /**
    * Increments the current value of the gauge the specified number of times.
    */
  def increment(times: Long): Gauge

  /**
    * Decrements the current value of the gauge by one unit.
    */
  def decrement(): Gauge

  /**
    * Decrements the current value of the gauge the specified number of times.
    */
  def decrement(times: Long): Gauge

  /**
    * Sets the current value of the gauge to the provided value.
    */
  def set(value: Long): Gauge

}

object Gauge {

  /**
    * Gauge implementation backed by an AtomicLong value.
    */
  class Atomic(val metric: BaseMetric[Gauge, Metric.Settings.ValueInstrument, Long], val tags: TagSet)
      extends Gauge with Instrument.Snapshotting[Long]
      with BaseMetricAutoUpdate[Gauge, Metric.Settings.ValueInstrument, Long] {

    private val _currentValue = new AtomicLong(0L)

    override def increment(): Gauge = {
      _currentValue.incrementAndGet()
      this
    }

    override def increment(times: Long): Gauge = {
      _currentValue.addAndGet(times)
      this
    }

    override def decrement(): Gauge = {
      _currentValue.decrementAndGet()
      this
    }

    override def decrement(times: Long): Gauge = {
      _currentValue.addAndGet(-times)
      this
    }

    override def set(value: Long): Gauge = {
      _currentValue.set(value)
      this
    }

    override def snapshot(resetState: Boolean): Long =
      _currentValue.get()

    override def baseMetric: BaseMetric[Gauge, Metric.Settings.ValueInstrument, Long] =
      metric
  }
}



