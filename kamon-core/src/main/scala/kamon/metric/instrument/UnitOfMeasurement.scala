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

/**
 *  A UnitOfMeasurement implementation describes the magnitude of a quantity being measured, such as Time and computer
 *  Memory space. Kamon uses UnitOfMeasurement implementations just as a informative companion to metrics inside entity
 *  recorders and might be used to scale certain kinds of measurements in metric backends.
 */
trait UnitOfMeasurement {
  def name: String
  def label: String
}

object UnitOfMeasurement {
  case object Unknown extends UnitOfMeasurement {
    val name = "unknown"
    val label = "unknown"
  }

  def isUnknown(uom: UnitOfMeasurement): Boolean =
    uom == Unknown

  def isTime(uom: UnitOfMeasurement): Boolean =
    uom.isInstanceOf[Time]

  def isMemory(uom: UnitOfMeasurement): Boolean =
    uom.isInstanceOf[Memory]

}

/**
 *  UnitOfMeasurement representing time.
 */
case class Time(factor: Double, label: String) extends UnitOfMeasurement {
  val name = "time"

  def scale(toUnit: Time)(value: Double): Double =
    (value * factor) / toUnit.factor
}

object Time {
  val Nanoseconds = Time(1E-9, "n")
  val Microseconds = Time(1E-6, "µs")
  val Milliseconds = Time(1E-3, "ms")
  val Seconds = Time(1, "s")
}

/**
 *  UnitOfMeasurement representing computer memory space.
 */
case class Memory(factor: Double, label: String) extends UnitOfMeasurement {
  val name = "bytes"

  def scale(toUnit: Memory)(value: Double): Double =
    (value * factor) / toUnit.factor
}

object Memory {
  val Bytes = Memory(1, "b")
  val KiloBytes = Memory(1024, "Kb")
  val MegaBytes = Memory(1024E2, "Mb")
  val GigaBytes = Memory(1024E3, "Gb")
}

