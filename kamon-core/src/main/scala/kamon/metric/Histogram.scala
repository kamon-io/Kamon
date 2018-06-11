/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon
package metric

import java.nio.ByteBuffer

import kamon.metric.SnapshotCreation.ZigZagCountsDistribution
import org.HdrHistogram.{AtomicHistogramExtension, HdrHistogramOps, SimpleHistogramExtension, ZigZag}
import org.slf4j.LoggerFactory

trait Histogram {
  def unit: MeasurementUnit
  def dynamicRange: DynamicRange

  def record(value: Long): Unit
  def record(value: Long, times: Long): Unit
}


private[kamon] class AtomicHdrHistogram(name: String, tags: Map[String, String], val unit: MeasurementUnit, val dynamicRange: DynamicRange)
    extends AtomicHistogramExtension(dynamicRange) with Histogram with SnapshotCreation {

  def record(value: Long): Unit =
    tryRecord(value, 1)

  def record(value: Long, count: Long): Unit =
    tryRecord(value, count)

  private[kamon] def snapshot(resetState: Boolean): MetricDistribution =
    snapshot(resetState, name, tags)

  private def tryRecord(value: Long, count: Long): Unit = {
    try {
      recordValueWithCount(value, count)
    } catch {
      case anyException: Throwable ⇒
        AtomicHdrHistogram.logger.warn(
          s"Failed to record value [$value] in histogram [$name]. You might need to change your dynamic range " +
          s"configuration for this instrument.", anyException)
    }
  }
}

private[kamon] class HdrHistogram(name: String, tags: Map[String, String], val unit: MeasurementUnit, val dynamicRange: DynamicRange)
  extends SimpleHistogramExtension(dynamicRange) with Histogram with SnapshotCreation {

  def record(value: Long): Unit =
    tryRecord(value, 1)

  def record(value: Long, count: Long): Unit =
    tryRecord(value, count)

  private[kamon] def snapshot(resetState: Boolean): MetricDistribution =
    snapshot(resetState, name, tags)

  private def tryRecord(value: Long, count: Long): Unit =
    recordValueWithCount(value, count)
}


private[kamon] trait SnapshotCreation {
  self: HdrHistogramOps with Histogram =>

  private[kamon] def snapshot(resetState: Boolean, name: String, tags: Map[String, String]): MetricDistribution = {
    val buffer = SnapshotCreation.tempSnapshotBuffer.get()
    val countsLimit = getCountsArraySize()
    var index = 0
    buffer.clear()

    var minIndex = Int.MaxValue
    var maxIndex = 0
    var totalCount = 0L

    while(index < countsLimit) {
      val countAtIndex = if(resetState) getAndSetFromCountsArray(index, 0L) else getFromCountsArray(index)

      var zerosCount = 0L
      if(countAtIndex == 0L) {
        index += 1
        zerosCount = 1
        while(index < countsLimit && getFromCountsArray(index) == 0L) {
          index += 1
          zerosCount += 1
        }
      }

      if(zerosCount > 0) {
        if(index < countsLimit)
          ZigZag.putLong(buffer, -zerosCount)
      }
      else {
        if(minIndex > index)
          minIndex = index
        maxIndex = index

        index += 1
        totalCount += countAtIndex
        ZigZag.putLong(buffer, countAtIndex)
      }
    }

    buffer.flip()
    val zigZagCounts = Array.ofDim[Byte](buffer.limit())
    buffer.get(zigZagCounts)

    val distribution = new ZigZagCountsDistribution(totalCount, minIndex, maxIndex, ByteBuffer.wrap(zigZagCounts).asReadOnlyBuffer(),
      protectedUnitMagnitude(), protectedSubBucketHalfCount(), protectedSubBucketHalfCountMagnitude())

    MetricDistribution(name, tags, unit, dynamicRange, distribution)
  }
}

private[kamon] object SnapshotCreation {
  // TODO: maybe make the buffer size configurable or make it auto-expanding.
  private val tempSnapshotBuffer = new ThreadLocal[ByteBuffer] {
    override def initialValue(): ByteBuffer = ByteBuffer.allocate(33792)
  }

  class ZigZagCountsDistribution(val count: Long, minIndex: Int, maxIndex: Int, zigZagCounts: ByteBuffer,
    unitMagnitude: Int, subBucketHalfCount: Int, subBucketHalfCountMagnitude: Int) extends Distribution {

    val min: Long = if(count == 0) 0 else bucketValueAtIndex(minIndex)
    val max: Long = bucketValueAtIndex(maxIndex)
    def sum: Long = bucketsIterator.foldLeft(0L)((a, b) => a + (b.value * b.frequency))

    def buckets: Seq[Bucket] = {
      val builder = Vector.newBuilder[Bucket]
      bucketsIterator.foreach { b =>
        builder += DefaultBucket(b.value, b.frequency)
      }

      builder.result()
    }

    def bucketsIterator: Iterator[Bucket] = new Iterator[Bucket] {
      val buffer = zigZagCounts.duplicate()
      val bucket = MutableBucket(0, 0)
      var countsArrayIndex = 0

      def hasNext: Boolean =
        buffer.remaining() > 0

      def next(): Bucket = {
        val readLong = ZigZag.getLong(buffer)
        val frequency = if(readLong > 0) {
          readLong
        } else {
          countsArrayIndex += (-readLong.toInt)
          ZigZag.getLong(buffer)
        }

        bucket.value = bucketValueAtIndex(countsArrayIndex)
        bucket.frequency = frequency
        countsArrayIndex += 1
        bucket
      }
    }

    def percentilesIterator: Iterator[Percentile] = new Iterator[Percentile]{
      val buckets = bucketsIterator
      val percentile = MutablePercentile(0D, 0, 0)
      var countUnderQuantile = 0L

      def hasNext: Boolean =
        buckets.hasNext

      def next(): Percentile = {
        val bucket = buckets.next()
        countUnderQuantile += bucket.frequency
        percentile.quantile = (countUnderQuantile * 100D) / ZigZagCountsDistribution.this.count
        percentile.countUnderQuantile = countUnderQuantile
        percentile.value = bucket.value
        percentile
      }
    }

    def percentile(p: Double): Percentile = {
      val percentiles = percentilesIterator
      if(percentiles.hasNext) {
        var currentPercentile = percentiles.next()
        while(percentiles.hasNext && currentPercentile.quantile < p) {
          currentPercentile = percentiles.next()
        }

        currentPercentile

      } else DefaultPercentile(p, 0, 0)
    }


    def percentiles: Seq[Percentile] = {
      val builder = Vector.newBuilder[Percentile]
      percentilesIterator.foreach { p =>
        builder += DefaultPercentile(p.quantile, p.value, p.countUnderQuantile)
      }

      builder.result()
    }

    def countsArray(): ByteBuffer = {
      zigZagCounts.duplicate()
    }

    @inline private def bucketValueAtIndex(index: Int): Long = {
      var bucketIndex: Int = (index >> subBucketHalfCountMagnitude) - 1
      var subBucketIndex: Int = (index & (subBucketHalfCount - 1)) + subBucketHalfCount
      if (bucketIndex < 0) {
        subBucketIndex -= subBucketHalfCount
        bucketIndex = 0
      }

      subBucketIndex.toLong << (bucketIndex + unitMagnitude)
    }
  }

  case class DefaultBucket(value: Long, frequency: Long) extends Bucket
  case class MutableBucket(var value: Long, var frequency: Long) extends Bucket

  case class DefaultPercentile(quantile: Double, value: Long, countUnderQuantile: Long) extends Percentile
  case class MutablePercentile(var quantile: Double, var value: Long, var countUnderQuantile: Long) extends Percentile
}

object AtomicHdrHistogram {
  private val logger = LoggerFactory.getLogger(classOf[AtomicHdrHistogram])
}