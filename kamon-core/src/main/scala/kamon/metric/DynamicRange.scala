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

package kamon.metric

import java.util.concurrent.TimeUnit

case class DynamicRange(lowestDiscernibleValue: Long, highestTrackableValue: Long, significantValueDigits: Int) {
  def upTo(highestTrackableValue: Long): DynamicRange =
    copy(highestTrackableValue = highestTrackableValue)

  def withLowestDiscernibleValue(lowestDiscernibleValue: Long): DynamicRange =
    copy(lowestDiscernibleValue = lowestDiscernibleValue)
}

object DynamicRange {
  private val oneHourInNanoseconds = TimeUnit.HOURS.toNanos(1)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 1 significant digit (10%)
    * across that range.
    */
  val Loose = DynamicRange(1L, oneHourInNanoseconds, 1)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 2 significant digit (1%)
    * across that range.
    */
  val Default = DynamicRange(1L, oneHourInNanoseconds, 2)

  /**
    * Provides a range from 0 to 3.6e+12 (one hour in nanoseconds) with a value precision of 3 significant digit (0.1%)
    * across that range.
    */
  val Fine = DynamicRange(1L, oneHourInNanoseconds, 3)
}
