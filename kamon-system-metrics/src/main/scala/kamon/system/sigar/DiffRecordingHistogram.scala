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

package kamon.system.sigar

import java.util.concurrent.atomic.AtomicLong

import kamon.metric.instrument.{ CollectionContext, Histogram }

/**
 *  Wrapper Histogram for cases in which the recorded values should always be the difference
 *  between the current value and the last recorded value. This is not thread-safe and only
 *  to be used with Sigar-based metrics that are securely updated within an actor.
 */
class DiffRecordingHistogram(wrappedHistogram: Histogram) extends Histogram {
  @volatile private var _recordedAtLeastOnce = false
  private val _lastObservedValue = new AtomicLong(0)

  private def processRecording(value: Long, count: Long): Unit = {
    if (_recordedAtLeastOnce) {
      val diff = value - _lastObservedValue.getAndSet(value)
      val current = if (diff >= 0) diff else 0L

      wrappedHistogram.record(current, count)
    } else {
      _lastObservedValue.set(value)
      _recordedAtLeastOnce = true
    }
  }

  def record(value: Long): Unit =
    processRecording(value, 1)

  def record(value: Long, count: Long): Unit =
    processRecording(value, count)

  def cleanup: Unit =
    wrappedHistogram.cleanup

  def collect(context: CollectionContext): Histogram.Snapshot =
    wrappedHistogram.collect(context)
}

object DiffRecordingHistogram {
  def apply(histogram: Histogram): DiffRecordingHistogram =
    new DiffRecordingHistogram(histogram)
}