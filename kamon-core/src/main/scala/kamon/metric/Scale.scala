/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metric

class Scale(val numericValue: Double) extends AnyVal

object Scale {
  val Nano = new Scale(1E-9)
  val Micro = new Scale(1E-6)
  val Milli = new Scale(1E-3)
  val Unit = new Scale(1)
  val Kilo = new Scale(1E3)
  val Mega = new Scale(1E6)
  val Giga = new Scale(1E9)

  def convert(fromUnit: Scale, toUnit: Scale, value: Long): Double = (value * fromUnit.numericValue) / toUnit.numericValue
}
