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

package org.HdrHistogram

import java.util.concurrent.atomic.AtomicLongFieldUpdater
import scala.annotation.tailrec
import org.HdrHistogram.AtomicSnapshotableHistogram.{Value, Snapshot}


/**
 *  This implementation aims to be used for real time data collection where data snapshots are taken often over time.
 *  The snapshotAndReset() operation extracts all the recorded values from the histogram and resets the counts, but still
 *  leave it in a consistent state even in the case of concurrent modification while the snapshot is being taken.
 */
class AtomicSnapshotableHistogram(highestTrackableValue: Long, numberOfSignificantValueDigits: Int)
  extends AtomicHistogram(1L, highestTrackableValue, numberOfSignificantValueDigits) {

  import AtomicSnapshotableHistogram.totalCountUpdater


  def snapshotAndReset(): Snapshot = {
    val entries = Vector.newBuilder[Value]
    val countsLength = counts.length()

    @tailrec def iterate(index: Int, previousValue: Long, nrOfRecordings: Long, bucketLimit: Long, increment: Long): Long = {
      if(index < countsLength) {
        val currentValue = previousValue + increment
        val countAtValue = counts.getAndSet(index, 0)

        if(countAtValue > 0)
          entries += Value(currentValue, countAtValue)


        if(currentValue == bucketLimit)
          iterate(index + 1, currentValue, nrOfRecordings + countAtValue, (bucketLimit << 1) + 1, increment << 1)
        else
          iterate(index + 1, currentValue, nrOfRecordings + countAtValue, bucketLimit, increment)
      } else {
        nrOfRecordings
      }
    }

    val nrOfRecordings = iterate(0, -1, 0, subBucketMask, 1)

    def tryUpdateTotalCount: Boolean = {
      val previousTotalCount = getTotalCount
      val newTotalCount = previousTotalCount - nrOfRecordings

      totalCountUpdater.compareAndSet(this, previousTotalCount, newTotalCount)
    }

    while (!tryUpdateTotalCount) {}

    Snapshot(nrOfRecordings, entries.result())
  }

}

object AtomicSnapshotableHistogram {
  val totalCountUpdater = AtomicLongFieldUpdater.newUpdater(classOf[AtomicHistogram], "totalCount")

  case class Snapshot(nrOfRecordings: Long, values: Vector[Value])
  case class Value(magnitude: Long, count: Long)
}
