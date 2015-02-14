/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
 * This class implements an extremely efficient, thread-safe way to generate a
 * incrementing sequence of Longs with a simple Long overflow protection.
 */
class Sequencer {
  private val CloseToOverflow = Long.MaxValue - 1000000000
  private val sequenceNumber = new PaddedAtomicLong(1L)

  def next(): Long = {
    val current = sequenceNumber.getAndIncrement

    // check if this value is getting close to overflow?
    if (current > CloseToOverflow) {
      sequenceNumber.set(current - CloseToOverflow) // we need maintain the order
    }
    current
  }
}

object Sequencer {
  def apply(): Sequencer = new Sequencer()
}