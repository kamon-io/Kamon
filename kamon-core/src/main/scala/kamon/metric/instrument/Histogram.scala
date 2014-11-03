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

package kamon.metric.instrument

import java.nio.LongBuffer
import com.typesafe.config.Config
import org.HdrHistogram.AtomicHistogramFieldsAccessor
import org.HdrHistogram.AtomicHistogram
import kamon.metric._

trait Histogram extends MetricRecorder {
  type SnapshotType = Histogram.Snapshot

  def record(value: Long)
  def record(value: Long, count: Long)
}

object Histogram {

  def apply(highestTrackableValue: Long, precision: Precision, scale: Scale): Histogram =
    new HdrHistogram(1L, highestTrackableValue, precision.significantDigits, scale)

  def fromConfig(config: Config): Histogram = {
    fromConfig(config, Scale.Unit)
  }

  def fromConfig(config: Config, scale: Scale): Histogram = {
    val highest = config.getLong("highest-trackable-value")
    val significantDigits = config.getInt("significant-value-digits")

    new HdrHistogram(1L, highest, significantDigits, scale)
  }

  object HighestTrackableValue {
    val OneHourInNanoseconds = 3600L * 1000L * 1000L * 1000L
  }

  case class Precision(significantDigits: Int)
  object Precision {
    val Low = Precision(1)
    val Normal = Precision(2)
    val Fine = Precision(3)
  }

  trait Record {
    def level: Long
    def count: Long

    private[kamon] def rawCompactRecord: Long
  }

  case class MutableRecord(var level: Long, var count: Long) extends Record {
    var rawCompactRecord: Long = 0L
  }

  trait Snapshot extends MetricSnapshot {
    type SnapshotType = Histogram.Snapshot

    def isEmpty: Boolean = numberOfMeasurements == 0
    def scale: Scale
    def numberOfMeasurements: Long
    def min: Long
    def max: Long
    def sum: Long
    def percentile(percentile: Double): Long
    def recordsIterator: Iterator[Record]
    def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot
  }

  object Snapshot {
    def empty(targetScale: Scale) = new Snapshot {
      override def min: Long = 0L
      override def max: Long = 0L
      override def sum: Long = 0L
      override def percentile(percentile: Double): Long = 0L
      override def recordsIterator: Iterator[Record] = Iterator.empty
      override def merge(that: Snapshot, context: CollectionContext): Snapshot = that
      override def scale: Scale = targetScale
      override def numberOfMeasurements: Long = 0L
    }
  }
}

/**
 *  This implementation is meant to be used for real time data collection where data snapshots are taken often over time.
 *  The collect(..) operation extracts all the recorded values from the histogram and resets the counts, but still
 *  leave it in a consistent state even in the case of concurrent modification while the snapshot is being taken.
 */
class HdrHistogram(lowestTrackableValue: Long, highestTrackableValue: Long, significantValueDigits: Int, scale: Scale = Scale.Unit)
    extends AtomicHistogram(lowestTrackableValue, highestTrackableValue, significantValueDigits)
    with Histogram with AtomicHistogramFieldsAccessor {

  import AtomicHistogramFieldsAccessor.totalCountUpdater

  def record(value: Long): Unit = recordValue(value)

  def record(value: Long, count: Long): Unit = recordValueWithCount(value, count)

  def collect(context: CollectionContext): Histogram.Snapshot = {
    import context.buffer
    buffer.clear()
    val nrOfMeasurements = writeSnapshotTo(buffer)

    buffer.flip()

    val measurementsArray = Array.ofDim[Long](buffer.limit())
    buffer.get(measurementsArray, 0, measurementsArray.length)
    new CompactHdrSnapshot(scale, nrOfMeasurements, measurementsArray, unitMagnitude(), subBucketHalfCount(), subBucketHalfCountMagnitude())
  }

  def getCounts = countsArray().length()

  def cleanup: Unit = {}

  private def writeSnapshotTo(buffer: LongBuffer): Long = {
    val counts = countsArray()
    val countsLength = counts.length()

    var nrOfMeasurements = 0L
    var index = 0L
    while (index < countsLength) {
      val countAtIndex = counts.getAndSet(index.toInt, 0L)

      if (countAtIndex > 0) {
        buffer.put(CompactHdrSnapshot.compactRecord(index, countAtIndex))
        nrOfMeasurements += countAtIndex
      }

      index += 1
    }

    reestablishTotalCount(nrOfMeasurements)
    nrOfMeasurements
  }

  private def reestablishTotalCount(diff: Long): Unit = {
    def tryUpdateTotalCount: Boolean = {
      val previousTotalCount = totalCountUpdater.get(this)
      val newTotalCount = previousTotalCount - diff

      totalCountUpdater.compareAndSet(this, previousTotalCount, newTotalCount)
    }

    while (!tryUpdateTotalCount) {}
  }

}

case class CompactHdrSnapshot(val scale: Scale, val numberOfMeasurements: Long, compactRecords: Array[Long], unitMagnitude: Int,
    subBucketHalfCount: Int, subBucketHalfCountMagnitude: Int) extends Histogram.Snapshot {

  def min: Long = if (compactRecords.length == 0) 0 else levelFromCompactRecord(compactRecords(0))
  def max: Long = if (compactRecords.length == 0) 0 else levelFromCompactRecord(compactRecords(compactRecords.length - 1))
  def sum: Long = recordsIterator.foldLeft(0L)((a, r) ⇒ a + (r.count * r.level))

  def percentile(p: Double): Long = {
    val records = recordsIterator
    val threshold = numberOfMeasurements * (p / 100D)
    var countToCurrentLevel = 0L
    var percentileLevel = 0L

    while (countToCurrentLevel < threshold && records.hasNext) {
      val record = records.next()
      countToCurrentLevel += record.count
      percentileLevel = record.level
    }

    percentileLevel
  }

  def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot = {
    if (that.isEmpty) this else if (this.isEmpty) that else {
      import context.buffer
      buffer.clear()

      val selfIterator = recordsIterator
      val thatIterator = that.recordsIterator
      var thatCurrentRecord: Histogram.Record = null
      var mergedNumberOfMeasurements = 0L

      def nextOrNull(iterator: Iterator[Histogram.Record]): Histogram.Record = if (iterator.hasNext) iterator.next() else null
      def addToBuffer(compactRecord: Long): Unit = {
        mergedNumberOfMeasurements += countFromCompactRecord(compactRecord)
        buffer.put(compactRecord)
      }

      while (selfIterator.hasNext) {
        val selfCurrentRecord = selfIterator.next()

        // Advance that to no further than the level of selfCurrentRecord
        thatCurrentRecord = if (thatCurrentRecord == null) nextOrNull(thatIterator) else thatCurrentRecord
        while (thatCurrentRecord != null && thatCurrentRecord.level < selfCurrentRecord.level) {
          addToBuffer(thatCurrentRecord.rawCompactRecord)
          thatCurrentRecord = nextOrNull(thatIterator)
        }

        // Include the current record of self and optionally merge if has the same level as thatCurrentRecord
        if (thatCurrentRecord != null && thatCurrentRecord.level == selfCurrentRecord.level) {
          addToBuffer(mergeCompactRecords(thatCurrentRecord.rawCompactRecord, selfCurrentRecord.rawCompactRecord))
          thatCurrentRecord = nextOrNull(thatIterator)
        } else {
          addToBuffer(selfCurrentRecord.rawCompactRecord)
        }
      }

      // Include everything that might have been left from that
      if (thatCurrentRecord != null) addToBuffer(thatCurrentRecord.rawCompactRecord)
      while (thatIterator.hasNext) {
        addToBuffer(thatIterator.next().rawCompactRecord)
      }

      buffer.flip()
      val compactRecords = Array.ofDim[Long](buffer.limit())
      buffer.get(compactRecords)

      new CompactHdrSnapshot(scale, mergedNumberOfMeasurements, compactRecords, unitMagnitude, subBucketHalfCount, subBucketHalfCountMagnitude)
    }
  }

  @inline private def mergeCompactRecords(left: Long, right: Long): Long = {
    val index = left >> 48
    val leftCount = countFromCompactRecord(left)
    val rightCount = countFromCompactRecord(right)

    CompactHdrSnapshot.compactRecord(index, leftCount + rightCount)
  }

  @inline private def levelFromCompactRecord(compactRecord: Long): Long = {
    val countsArrayIndex = (compactRecord >> 48).toInt
    var bucketIndex: Int = (countsArrayIndex >> subBucketHalfCountMagnitude) - 1
    var subBucketIndex: Int = (countsArrayIndex & (subBucketHalfCount - 1)) + subBucketHalfCount
    if (bucketIndex < 0) {
      subBucketIndex -= subBucketHalfCount
      bucketIndex = 0
    }

    subBucketIndex.toLong << (bucketIndex + unitMagnitude)
  }

  @inline private def countFromCompactRecord(compactRecord: Long): Long =
    compactRecord & CompactHdrSnapshot.CompactRecordCountMask

  def recordsIterator: Iterator[Histogram.Record] = new Iterator[Histogram.Record] {
    var currentIndex = 0
    val mutableRecord = Histogram.MutableRecord(0, 0)

    override def hasNext: Boolean = currentIndex < compactRecords.length

    override def next(): Histogram.Record = {
      if (hasNext) {
        val measurement = compactRecords(currentIndex)
        mutableRecord.rawCompactRecord = measurement
        mutableRecord.level = levelFromCompactRecord(measurement)
        mutableRecord.count = countFromCompactRecord(measurement)
        currentIndex += 1

        mutableRecord
      } else {
        throw new IllegalStateException("The iterator has already been consumed.")
      }
    }
  }
}

object CompactHdrSnapshot {
  val CompactRecordCountMask = 0xFFFFFFFFFFFFL

  def compactRecord(index: Long, count: Long): Long = (index << 48) | count
}