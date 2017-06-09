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

import java.util.concurrent.atomic.AtomicLong

import scala.annotation.tailrec

class AtomicLongMaxUpdater(value:AtomicLong) {

  def update(newMax:Long):Unit = {
    @tailrec def compare():Long = {
      val currentMax = value.get()
      if(newMax > currentMax) if (!value.compareAndSet(currentMax, newMax)) compare() else newMax
      else currentMax
    }
    compare()
  }

  def maxThenReset(newValue:Long): Long =
    value.getAndSet(newValue)
}

object AtomicLongMaxUpdater {
  def apply(): AtomicLongMaxUpdater =
    new AtomicLongMaxUpdater(new AtomicLong(0))
}