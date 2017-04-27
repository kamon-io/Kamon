package kamon
package metric
package instrument

import java.util.concurrent.atomic.LongAdder

import com.typesafe.scalalogging.StrictLogging
import kamon.util.MeasurementUnit

trait Counter {
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
}

class LongAdderCounter(entity: Entity, name: String, val measurementUnit: MeasurementUnit)
    extends Counter with SingleValueSnapshotInstrument with StrictLogging {

  private val adder = new LongAdder()

  def increment(): Unit =
    adder.increment()

  def increment(times: Long): Unit = {
    if (times >= 0)
      adder.add(times)
    else
      logger.warn(s"Ignored attempt to decrement counter [$name] on entity [$entity]")
  }

  def snapshot(): SingleValueSnapshot =
    SingleValueSnapshot(name, measurementUnit, adder.sumThenReset())
}
