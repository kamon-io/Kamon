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

import kamon.metric.instrument.Histogram.{ DynamicRange, Snapshot }
import org.HdrHistogram.ModifiedAtomicHistogram

trait Histogram extends Instrument {
  type SnapshotType = Histogram.Snapshot

  def record(value: Long)
  def record(value: Long, count: Long)
}

object Histogram {

  /**
   *  Scala API:
   *
   *  Create a new High Dynamic Range Histogram ([[kamon.metric.instrument.HdrHistogram]]) using the given
   *  [[kamon.metric.instrument.Histogram.DynamicRange]].
   */
  def apply(dynamicRange: DynamicRange): Histogram = new HdrHistogram(dynamicRange)

  /**
   *  Java API:
   *
   *  Create a new High Dynamic Range Histogram ([[kamon.metric.instrument.HdrHistogram]]) using the given
   *  [[kamon.metric.instrument.Histogram.DynamicRange]].
   */
  def create(dynamicRange: DynamicRange): Histogram = apply(dynamicRange)

  /**
   *  DynamicRange is a configuration object used to supply range and precision configuration to a
   *  [[kamon.metric.instrument.HdrHistogram]]. See the [[http://hdrhistogram.github.io/HdrHistogram/ HdrHistogram website]]
   *  for more details on how it works and the effects of these configuration values.
   *
   * @param lowestDiscernibleValue
   *    The lowest value that can be discerned (distinguished from 0) by the histogram.Must be a positive integer that
   *    is >= 1. May be internally rounded down to nearest power of 2.
   * @param highestTrackableValue
   *    The highest value to be tracked by the histogram. Must be a positive integer that is >= (2 * lowestDiscernibleValue).
   *    Must not be larger than (Long.MAX_VALUE/2).
   * @param precision
   *    The number of significant decimal digits to which the histogram will maintain value resolution and separation.
   *    Must be a non-negative integer between 1 and 3.
   */
  case class DynamicRange(lowestDiscernibleValue: Long, highestTrackableValue: Long, precision: Int)

  trait Record {
    def level: Long
    def count: Long

    private[kamon] def rawCompactRecord: Long
  }

  case class MutableRecord(var level: Long, var count: Long) extends Record {
    var rawCompactRecord: Long = 0L
  }

  trait Snapshot extends InstrumentSnapshot {

    def isEmpty: Boolean = numberOfMeasurements == 0
    def numberOfMeasurements: Long
    def min: Long
    def max: Long
    def sum: Long
    def percentile(percentile: Double): Long
    def recordsIterator: Iterator[Record]
    def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot
    def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot

    override def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): Histogram.Snapshot =
      new ScaledSnapshot(from, to, this)
  }

  class ScaledSnapshot(from: UnitOfMeasurement, to: UnitOfMeasurement, snapshot: Snapshot) extends Snapshot {
    private def doScale(v: Long) = from.tryScale(to)(v).toLong
    override def numberOfMeasurements: Long = snapshot.numberOfMeasurements

    override def max: Long = doScale(snapshot.max)

    override def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot = snapshot.merge(that, context)

    override def merge(that: Snapshot, context: CollectionContext): Snapshot = snapshot.merge(that, context)

    override def percentile(percentile: Double): Long = doScale(snapshot.percentile(percentile))

    override def min: Long = doScale(snapshot.min)

    override def sum: Long = doScale(snapshot.sum)

    override def recordsIterator: Iterator[Record] = {
      snapshot.recordsIterator.map(record ⇒ new Record {
        override def count: Long = record.count

        override def level: Long = doScale(record.level)

        override private[kamon] def rawCompactRecord: Long = record.rawCompactRecord
      })
    }

    override def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): Histogram.Snapshot =
      if (this.from == from && this.to == to) this else super.scale(from, to)
  }

  object Snapshot {
    val empty = new Snapshot {
      override def min: Long = 0L
      override def max: Long = 0L
      override def sum: Long = 0L
      override def percentile(percentile: Double): Long = 0L
      override def recordsIterator: Iterator[Record] = Iterator.empty
      override def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot = that
      override def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot = that
      override def numberOfMeasurements: Long = 0L
      override def scale(from: UnitOfMeasurement, to: UnitOfMeasurement): Histogram.Snapshot = this
    }
  }
}

/**
 *  This implementation is meant to be used for real time data collection where data snapshots are taken often over time.
 *  The collect(..) operation extracts all the recorded values from the histogram and resets the counts, but still
 *  leave it in a consistent state even in the case of concurrent modification while the snapshot is being taken.
 */
class HdrHistogram(dynamicRange: DynamicRange) extends ModifiedAtomicHistogram(dynamicRange.lowestDiscernibleValue,
  dynamicRange.highestTrackableValue, dynamicRange.precision) with Histogram {

  def record(value: Long): Unit = recordValue(value)

  def record(value: Long, count: Long): Unit = recordValueWithCount(value, count)

  def collect(context: CollectionContext): Histogram.Snapshot = {
    import context.buffer
    buffer.clear()
    val nrOfMeasurements = writeSnapshotTo(buffer)

    buffer.flip()

    val measurementsArray = Array.ofDim[Long](buffer.limit())
    buffer.get(measurementsArray, 0, measurementsArray.length)
    new CompactHdrSnapshot(nrOfMeasurements, measurementsArray, protectedUnitMagnitude(), protectedSubBucketHalfCount(), protectedSubBucketHalfCountMagnitude())
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
    nrOfMeasurements
  }
}

case class CompactHdrSnapshot(val numberOfMeasurements: Long, compactRecords: Array[Long], unitMagnitude: Int,
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

  def merge(that: Histogram.Snapshot, context: CollectionContext): Snapshot =
    merge(that.asInstanceOf[InstrumentSnapshot], context)

  def merge(that: InstrumentSnapshot, context: CollectionContext): Histogram.Snapshot = that match {
    case thatSnapshot: CompactHdrSnapshot ⇒
      if (thatSnapshot.isEmpty) this else if (this.isEmpty) thatSnapshot else {
        import context.buffer
        buffer.clear()

        val selfIterator = recordsIterator
        val thatIterator = thatSnapshot.recordsIterator
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

        new CompactHdrSnapshot(mergedNumberOfMeasurements, compactRecords, unitMagnitude, subBucketHalfCount, subBucketHalfCountMagnitude)
      }

    case other ⇒
      sys.error(s"Cannot merge a CompactHdrSnapshot with the incompatible [${other.getClass.getName}] type.")

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