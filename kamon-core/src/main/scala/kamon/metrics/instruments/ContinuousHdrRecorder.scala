/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metrics.instruments

import org.HdrHistogram.HdrRecorder
import kamon.metrics.{ Scale, MetricSnapshotLike }

/**
 *  This recorder keeps track of the last value recoded and automatically adds it after collecting a snapshot. This is
 *  useful in cases where the absence of recordings does not necessarily mean the absence of values. For example, if this
 *  recorder is used for recording the mailbox size of an actor, and it only gets updated upon message enqueue o dequeue,
 *  the absence of recordings during 1 second means that the size hasn't change (example: the actor being blocked doing
 *  some work) and it should keep its last known value, instead of dropping to zero and then going back to the real value
 *  after a new event is processed.
 *
 */
class ContinuousHdrRecorder(highestTrackableValue: Long, significantValueDigits: Int, scale: Scale)
    extends HdrRecorder(highestTrackableValue, significantValueDigits, scale) {

  @volatile private var lastRecordedValue: Long = 0

  override def record(value: Long): Unit = {
    lastRecordedValue = value
    super.record(value)
  }

  override def collect(): MetricSnapshotLike = {
    val snapshot = super.collect()
    super.record(lastRecordedValue)

    snapshot
  }
}

object ContinuousHdrRecorder {
  def apply(highestTrackableValue: Long, significantValueDigits: Int, scale: Scale) =
    new ContinuousHdrRecorder(highestTrackableValue, significantValueDigits, scale)
}