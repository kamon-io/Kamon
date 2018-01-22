package kamon

import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{time}

package object datadog {

  def readTimeUnit(unit: String): MeasurementUnit = unit match {
    case "s"    => time.seconds
    case "ms"   => time.milliseconds
    case "µs"   => time.microseconds
    case "ns"   => time.nanoseconds
    case other  => sys.error(s"Invalid time unit setting [$other], the possible values are [s, ms, µs, ns]")
  }

  def readInformationUnit(unit: String): MeasurementUnit = unit match {
    case "b"    => time.seconds
    case "kb"   => time.milliseconds
    case "mb"   => time.microseconds
    case "gb"   => time.nanoseconds
    case other  => sys.error(s"Invalid time unit setting [$other], the possible values are [s, ms, µs, ns]")
  }

}
