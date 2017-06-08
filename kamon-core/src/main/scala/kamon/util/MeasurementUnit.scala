/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.util

import kamon.util.MeasurementUnit.{Dimension, Magnitude}

/**
  * A MeasurementUnit is a simple representation of the dimension and magnitude of a quantity being measured, such as
  * "Time in Seconds" or "Data in Kilobytes".
  */
case class MeasurementUnit(dimension: Dimension, magnitude: Magnitude)

object MeasurementUnit {
  val none = MeasurementUnit(Dimension.None, Magnitude("none", 1D))

  val time: TimeUnits = new TimeUnits {
    val seconds = MeasurementUnit(Dimension.Time, Magnitude("seconds", 1D))
    val milliseconds = MeasurementUnit(Dimension.Time, Magnitude("milliseconds", 1e-3))
    val microseconds = MeasurementUnit(Dimension.Time, Magnitude("microseconds", 1e-6))
    val nanoseconds = MeasurementUnit(Dimension.Time, Magnitude("nanoseconds", 1e-9))
  }

  val information: DataUnits = new DataUnits {
    val bytes = MeasurementUnit(Dimension.Information, Magnitude("byte", 1))
    val kilobytes = MeasurementUnit(Dimension.Information, Magnitude("kilobytes", 1024))
    val megabytes = MeasurementUnit(Dimension.Information, Magnitude("megabytes", 1024 * 1024))
    val gigabytes = MeasurementUnit(Dimension.Information, Magnitude("gigabytes", 1024 * 1024 * 1024))
  }

  /**
    * Scales the provided value between two MeasurementUnits of the same dimension.
    *
    * @param value value to be scaled.
    * @param from value's [[MeasurementUnit]].
    * @param to target [[MeasurementUnit]].
    * @return equivalent of the provided value on the target [[MeasurementUnit]]
    */
  def scale(value: Long, from: MeasurementUnit, to: MeasurementUnit): Double = {
    if(from.dimension != to.dimension)
      sys.error(s"Can't scale values from the [${from.dimension.name}] dimension into the [${to.dimension.name}] dimension.")
    else if(from == to)
      value.toDouble
    else (from.magnitude.scaleFactor / to.magnitude.scaleFactor) * value.toDouble
  }

  case class Dimension(name: String)
  case class Magnitude(name: String, scaleFactor: Double)

  object Dimension {
    val None = Dimension("none")
    val Time = Dimension("time")
    val Information = Dimension("information")
  }

  trait TimeUnits {
    def seconds: MeasurementUnit
    def milliseconds: MeasurementUnit
    def microseconds: MeasurementUnit
    def nanoseconds: MeasurementUnit
  }

  trait DataUnits {
    def bytes: MeasurementUnit
    def kilobytes: MeasurementUnit
    def megabytes: MeasurementUnit
    def gigabytes: MeasurementUnit
  }
}
