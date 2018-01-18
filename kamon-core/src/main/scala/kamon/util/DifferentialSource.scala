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

/**
  * Keeps track of the values produced by the source and produce the difference between the last two observed values
  * when calling get. This class assumes the source increases monotonically and any produced value that violates this
  * assumption will be dropped.
  *
  */
class DifferentialSource(source: () => Long) {
  private var previousValue = source()

  def get(): Long = synchronized {
    val currentValue = source()
    val diff = currentValue - previousValue
    previousValue = currentValue

    if(diff < 0) 0 else diff
  }
}

object DifferentialSource {
  def apply(source: () => Long): DifferentialSource =
    new DifferentialSource(source)
}
