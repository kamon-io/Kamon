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

package kamon.metric.instrument

import kamon.jsr166.LongAdder

trait Counter extends Instrument {
  type SnapshotType = Counter.Snapshot

  def increment(): Unit
  def increment(times: Long): Unit
}

object Counter {

  def apply(): Counter = new LongAdderCounter
  def create(): Counter = apply()

  trait Snapshot extends InstrumentSnapshot {
    def count: Long
    def merge(that: InstrumentSnapshot, context: CollectionContext): Counter.Snapshot
    def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): Counter.Snapshot
  }
}

class LongAdderCounter extends Counter {
  private val counter = new LongAdder

  def increment(): Unit = counter.increment()

  def increment(times: Long): Unit = {
    if (times < 0)
      throw new UnsupportedOperationException("Counters cannot be decremented")
    counter.add(times)
  }

  def collect(context: CollectionContext): Counter.Snapshot = CounterSnapshot(counter.sumThenReset())

  def cleanup: Unit = {}
}

case class CounterSnapshot(count: Long) extends Counter.Snapshot {
  def merge(that: InstrumentSnapshot, context: CollectionContext): Counter.Snapshot = that match {
    case CounterSnapshot(thatCount) ⇒ CounterSnapshot(count + thatCount)
    case other                      ⇒ sys.error(s"Cannot merge a CounterSnapshot with the incompatible [${other.getClass.getName}] type.")
  }

  override def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): Counter.Snapshot =
    CounterSnapshot(from.tryScale(to)(count).toLong)

}