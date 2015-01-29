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

import akka.actor.{ Scheduler, Cancellable }
import akka.dispatch.MessageDispatcher
import scala.concurrent.duration.FiniteDuration

private[kamon] trait Instrument {
  type SnapshotType <: InstrumentSnapshot

  def collect(context: CollectionContext): SnapshotType
  def cleanup: Unit
}

trait InstrumentSnapshot {
  def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot
}

class InstrumentType private[kamon] (val id: Int) extends AnyVal
object InstrumentTypes {
  val Histogram = new InstrumentType(1)
  val MinMaxCounter = new InstrumentType(2)
  val Gauge = new InstrumentType(3)
  val Counter = new InstrumentType(4)
}

trait CollectionContext {
  def buffer: LongBuffer
}

object CollectionContext {
  def apply(longBufferSize: Int): CollectionContext = new CollectionContext {
    val buffer: LongBuffer = LongBuffer.allocate(longBufferSize)
  }
}

trait RefreshScheduler {
  def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable
}

object RefreshScheduler {
  val NoopScheduler = new RefreshScheduler {
    def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable = new Cancellable {
      override def isCancelled: Boolean = true
      override def cancel(): Boolean = true
    }
  }

  def apply(scheduler: Scheduler, dispatcher: MessageDispatcher): RefreshScheduler = new RefreshScheduler {
    def schedule(interval: FiniteDuration, refresh: () ⇒ Unit): Cancellable =
      scheduler.schedule(interval, interval)(refresh.apply())(dispatcher)
  }

  def create(scheduler: Scheduler, dispatcher: MessageDispatcher): RefreshScheduler = apply(scheduler, dispatcher)
}