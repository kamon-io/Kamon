package kamon.metric

import java.util.concurrent.atomic.LongAdder

import com.typesafe.scalalogging.StrictLogging
import kamon.util.MeasurementUnit

trait Counter {
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
}

class LongAdderCounter(name: String, tags: Map[String, String], val measurementUnit: MeasurementUnit)
    extends SnapshotableCounter with StrictLogging {

  private val adder = new LongAdder()

  def increment(): Unit =
    adder.increment()

  def increment(times: Long): Unit = {
    if (times >= 0)
      adder.add(times)
    else
      logger.warn(s"Ignored attempt to decrement counter [$name]")
  }

  def snapshot(): MetricValue =
    MetricValue(name, tags, measurementUnit, adder.sumThenReset())
}
