package kamon.util

import com.typesafe.config.Config
import kamon.metric.instrument.{ Memory, Time, UnitOfMeasurement }
import kamon.util.ConfigTools._

class ScaleUnits(config: Config) {

  val TimeUnits = "time-units"
  val MemoryUnits = "memory-units"

  lazy val scaleTimeTo: Option[Time] =
    if (config.hasPath(TimeUnits)) Some(config.time(TimeUnits)) else None

  lazy val scaleMemoryTo: Option[Memory] =
    if (config.hasPath(MemoryUnits)) Some(config.memory(MemoryUnits)) else None

  def scale(unit: UnitOfMeasurement, value: Long): Long = (unit, scaleTimeTo, scaleMemoryTo) match {
    case (from: Time, Some(to), _)   ⇒ from.scale(to)(value).toLong
    case (from: Memory, _, Some(to)) ⇒ from.scale(to)(value).toLong
    case _                           ⇒ value
  }

}

