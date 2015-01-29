package kamon.metric.instrument

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

import java.lang.Math.abs
import java.util.concurrent.atomic.AtomicReference
import akka.actor.Cancellable
import kamon.jsr166.LongMaxUpdater
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.util.PaddedAtomicLong
import scala.concurrent.duration.FiniteDuration

trait MinMaxCounter extends Instrument {
  override type SnapshotType = Histogram.Snapshot

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement()
  def decrement(times: Long)
  def refreshValues(): Unit
}

object MinMaxCounter {

  def apply(dynamicRange: DynamicRange, refreshInterval: FiniteDuration, scheduler: RefreshScheduler): MinMaxCounter = {
    val underlyingHistogram = Histogram(dynamicRange)
    val minMaxCounter = new PaddedMinMaxCounter(underlyingHistogram)
    val refreshValuesSchedule = scheduler.schedule(refreshInterval, () ⇒ {
      minMaxCounter.refreshValues()
    })

    minMaxCounter.refreshValuesSchedule.set(refreshValuesSchedule)
    minMaxCounter
  }

  def create(dynamicRange: DynamicRange, refreshInterval: FiniteDuration, scheduler: RefreshScheduler): MinMaxCounter =
    apply(dynamicRange, refreshInterval, scheduler)

}

class PaddedMinMaxCounter(underlyingHistogram: Histogram) extends MinMaxCounter {
  private val min = new LongMaxUpdater(0L)
  private val max = new LongMaxUpdater(0L)
  private val sum = new PaddedAtomicLong
  val refreshValuesSchedule = new AtomicReference[Cancellable]()

  def increment(): Unit = increment(1L)

  def increment(times: Long): Unit = {
    val currentValue = sum.addAndGet(times)
    max.update(currentValue)
  }

  def decrement(): Unit = decrement(1L)

  def decrement(times: Long): Unit = {
    val currentValue = sum.addAndGet(-times)
    min.update(-currentValue)
  }

  def collect(context: CollectionContext): Histogram.Snapshot = {
    refreshValues()
    underlyingHistogram.collect(context)
  }

  def cleanup: Unit = {
    if (refreshValuesSchedule.get() != null)
      refreshValuesSchedule.get().cancel()
  }

  def refreshValues(): Unit = {
    val currentValue = {
      val value = sum.get()
      if (value <= 0) 0 else value
    }

    val currentMin = {
      val rawMin = min.maxThenReset(-currentValue)
      if (rawMin >= 0)
        0
      else
        abs(rawMin)
    }

    val currentMax = max.maxThenReset(currentValue)

    underlyingHistogram.record(currentValue)
    underlyingHistogram.record(currentMin)
    underlyingHistogram.record(currentMax)
  }
}
