/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.metric.instrument

trait UnitOfMeasurement {
  def name: String
  def label: String
  def factor: Double
}

object UnitOfMeasurement {
  case object Unknown extends UnitOfMeasurement {
    val name = "unknown"
    val label = "unknown"
    val factor = 1D
  }

  def isUnknown(uom: UnitOfMeasurement): Boolean =
    uom == Unknown

  def isTime(uom: UnitOfMeasurement): Boolean =
    uom.isInstanceOf[Time]

}

case class Time(factor: Double, label: String) extends UnitOfMeasurement {
  val name = "time"

  /**
   *  Scale a value from this scale factor to a different scale factor.
   *
   * @param toUnit Time unit of the expected result.
   * @param value Value to scale.
   * @return Equivalent of value on the target time unit.
   */
  def scale(toUnit: Time)(value: Long): Double =
    (value * factor) / toUnit.factor
}

object Time {
  val Nanoseconds = Time(1E-9, "n")
  val Microseconds = Time(1E-6, "µs")
  val Milliseconds = Time(1E-3, "ms")
  val Seconds = Time(1, "s")
}

case class Memory(factor: Double, label: String) extends UnitOfMeasurement {
  val name = "bytes"
}

object Memory {
  val Bytes = Memory(1, "b")
  val KiloBytes = Memory(1024, "Kb")
  val MegaBytes = Memory(1024E2, "Mb")
  val GigaBytes = Memory(1024E3, "Gb")
}

