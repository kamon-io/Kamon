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
package util

import org.slf4j.LoggerFactory

/**
  * Exponentially Weighted Moving Average implementation with thread safety guarantees. Users of this class should
  * periodically record values on the structure and are free to read the current average at any time. This
  * implementation does not make any assumptions regarding the time between recordings and leaves the responsibility of
  * deciding when to record values to the user.
  *
  * The only required parameter is the weighting factor, which effectively determines how quickly the moving average
  * will adapt to new data. The weighting factor is a number between 0 and 1 (exclusive). The closer the weighting
  * factor is to 0, the more influence new values will have over the moving average and thus the moving average is more
  * responsive; conversely, if the weighting factor is closer to 1, the moving average will take more iterations to
  * adapt to new data.
  */
class EWMA private (weightingFactor: Double) {
  @volatile private var _current = 0d
  private val _newDataWeightFactor = 1d - weightingFactor
  private var _count = 0L

  /**
    * Returns the current exponentially weighted moving average.
    */
  def average(): Double =
    _current

  /**
    * Return the number of recorded values.
    */
  def count(): Long =
    _count

  /**
    * Adds the provided value to the exponentially weighted moving average.
    */
  def add(value: Double): Unit = synchronized {
    val currentValue = (_current * weightingFactor) + (value * _newDataWeightFactor)

    _count += 1
    // Apply bias correction to the current value, if needed
    _current = if (_count > 1L) currentValue else currentValue / (1d - Math.pow(weightingFactor, _count))
  }
}

object EWMA {

  private val _logger = LoggerFactory.getLogger(classOf[EWMA])

  // This roughly means that the moving average is equivalent to the average of the last 10 values. A rough idea of the
  // number of values represented in the moving average can be calculated via consideredValues = 1 / (1 - factor)
  private val _fallbackWeightingFactor = 0.9d

  /**
    * Creates a new EWMA instance with a weighting factor of 0.9.
    */
  def create(): EWMA =
    create(_fallbackWeightingFactor)

  /**
    * Creates a new EWMA instance with the provided weighting factor. If the provided weighting factor is not between
    * 0 and 1 (exclusive) then it will be ignored and the default factor of 0.9 will be used.
    */
  def create(weightingFactor: Double): EWMA =
    if (weightingFactor > 0d && weightingFactor < 1d)
      new EWMA(weightingFactor)
    else {
      _logger.warn(
        s"Ignoring invalid weighting factor [$weightingFactor] and falling back to [${_fallbackWeightingFactor}]"
      )
      new EWMA(_fallbackWeightingFactor)
    }
}
