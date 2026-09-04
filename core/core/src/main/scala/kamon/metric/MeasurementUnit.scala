/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package metric

import org.slf4j.LoggerFactory

/**
  * A MeasurementUnit is a simple representation of the dimension and magnitude of a quantity being measured, such as
  * "Time in Seconds" or "Information in Kilobytes". The main use of these units is done by the metric instruments; when
  * an instrument has a specified MeasurementUnit the reporters can apply scaling in case it's necessary to meet the
  * backend's requirements.
  */
case class MeasurementUnit(
  dimension: MeasurementUnit.Dimension,
  magnitude: MeasurementUnit.Magnitude
)

object MeasurementUnit {

  private val _logger = LoggerFactory.getLogger("kamon.metric.MeasurementUnit")

  /**
    * Default measurement unit used when there is no knowledge of the actual unit being measured or none of the
    * available units matches the actual unit.
    */
  val none = MeasurementUnit(Dimension.None, Magnitude("none", 1d))

  /**
    * Unit for values that represent the ratio between two indicators, as a fraction of 100. Metrics using this unit
    * will usually have a range between 0 and 100, although there are no hard limitations on that.
    */
  val percentage = MeasurementUnit(Dimension.Percentage, Magnitude("percentage", 1d))

  /**
    * Group of units for measurements in the time dimension. All metrics tracking latency will use one of these units,
    * typically the nanoseconds unit.
    */
  val time: TimeUnits = new TimeUnits {
    val seconds = timeUnit("seconds", 1d)
    val milliseconds = timeUnit("milliseconds", 1e-3)
    val microseconds = timeUnit("microseconds", 1e-6)
    val nanoseconds = timeUnit("nanoseconds", 1e-9)
  }

  /**
    * Group of units for measurements in the information dimension. Metrics tracking indicators like message sizes,
    * memory usage or network traffic will typically use the bytes unit.
    */
  val information: InformationUnits = new InformationUnits {
    val bytes = informationUnit("byte", 1)
    val kilobytes = informationUnit("kilobytes", 1 << 10)
    val megabytes = informationUnit("megabytes", 1 << 20)
    val gigabytes = informationUnit("gigabytes", 1 << 30)
  }

  /**
    * Converts the provided value between two MeasurementUnits of the same dimension. If the "from" and "to" units do
    * not share the same dimension a warning will be logged and the value will be returned unchanged.
    */
  def convert(value: Double, from: MeasurementUnit, to: MeasurementUnit): Double = {
    if (from.dimension != to.dimension) {
      _logger.warn(
        s"Can't convert values from the [${from.dimension.name}] dimension into the [${to.dimension.name}] dimension."
      )
      value
    } else if (from == to)
      value
    else (from.magnitude.scaleFactor / to.magnitude.scaleFactor) * value
  }

  /**
    * Represents a named quantity within a particular dimension and the scale factor between that quantity and the
    * smallest quantity (base unit) of that dimension.
    */
  case class Magnitude(name: String, scaleFactor: Double)

  /**
    * A measurable extent of a particular kind. For example, the "time" dimension signals that measurements represent
    * the extent in time between two instants.
    */
  case class Dimension(name: String)

  object Dimension {
    val None = Dimension("none")
    val Time = Dimension("time")
    val Percentage = Dimension("percentage")
    val Information = Dimension("information")
  }

  /** Makes it easier to access the time units from non-Scala code */
  trait TimeUnits {
    def seconds: MeasurementUnit
    def milliseconds: MeasurementUnit
    def microseconds: MeasurementUnit
    def nanoseconds: MeasurementUnit
  }

  /** Makes it easier to access the information units from non-Scala code */
  trait InformationUnits {
    def bytes: MeasurementUnit
    def kilobytes: MeasurementUnit
    def megabytes: MeasurementUnit
    def gigabytes: MeasurementUnit
  }

  private def timeUnit(name: String, scaleFactor: Double): MeasurementUnit =
    MeasurementUnit(Dimension.Time, Magnitude(name, scaleFactor))

  private def informationUnit(name: String, scaleFactor: Double): MeasurementUnit =
    MeasurementUnit(Dimension.Information, Magnitude(name, scaleFactor))
}
