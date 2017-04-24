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

import java.nio.LongBuffer

private[kamon] trait Instrument {
  type SnapshotType <: InstrumentSnapshot

  def collect(context: CollectionContext): SnapshotType
  def cleanup: Unit
}

trait InstrumentSnapshot {
  def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot

  def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): InstrumentSnapshot
}

trait CollectionContext {
  def buffer: LongBuffer
}

object CollectionContext {
  def apply(longBufferSize: Int): CollectionContext = new CollectionContext {
    val buffer: LongBuffer = LongBuffer.allocate(longBufferSize)
  }
}

sealed trait InstrumentType

object InstrumentTypes {
  case object Histogram extends InstrumentType
  case object MinMaxCounter extends InstrumentType
  case object Gauge extends InstrumentType
  case object Counter extends InstrumentType
}
