/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.metric

import java.nio.ByteBuffer

import kamon.metric.Histogram.DistributionSnapshotBuilder
import org.HdrHistogram.{BaseLocalHdrHistogram, ZigZag}
import org.slf4j.LoggerFactory

/**
  * A distribution of values observed by an instrument. All Kamon distributions are based on the HdrHistogram and as
  * such, they represent a distribution of values within a configured range and precision. By default, all instruments
  * that generate distributions are configured to accept 1% error margin, meaning that all recorded values are adjusted
  * to the bucket whose value is up to 1% away from the original value.
  *
  * Distributions only expose data for non-empty buckets/percentiles.
  */
trait Distribution {

  /**
    * Describes the range and precision settings of the instrument from which this distribution was taken.
    */
  def dynamicRange: DynamicRange

  /**
    * Minimum value in this distribution.
    */
  def min: Long

  /**
    * Maximum value in this distribution.
    */
  def max: Long

  /**
    * Sum of all values in this distribution.
    */
  def sum: Long

  /**
    * Number of values stored in this distribution.
    */
  def count: Long

  /**
    * Returns the percentile at the specified rank.
    */
  def percentile(rank: Double): Distribution.Percentile

  /**
    * Returns an immutable list of all percentiles on this distribution. Calling this function requires allocation of
    * new percentile instances every time it is called, if you just need to read the values to transfer them into
    * another medium consider using a percentiles iterator instead.
    */
  def percentiles: Seq[Distribution.Percentile]

  /**
    * Returns an iterator of all percentile values on this distribution. This iterator uses a mutable percentiles
    * instance that gets updated as the user iterates through them to avoid allocating intermediary objects and thus,
    * trying to copy percentile instances from the iterator into other structures might not work as you expect.
    */
  def percentilesIterator: Iterator[Distribution.Percentile]

  /**
    * Returns an immutable list of all buckets on this distribution. Calling this function requires allocation of
    * new bucket instances every time it is called, if you just need to read the values to transfer them into
    * another medium consider using a buckets iterator instead.
    */
  def buckets: Seq[Distribution.Bucket]

  /**
    * Returns an iterator of all buckets on this distribution. This iterator uses a mutable buckets instance that gets
    * updated as the user iterates through them to avoid allocating intermediary objects and thus, trying to copy bucket
    * instances from the iterator into other structures might not work as you expect.
    */
  def bucketsIterator: Iterator[Distribution.Bucket]
}

object Distribution {

  private val _logger = LoggerFactory.getLogger(classOf[Distribution])

  /**
    * Describes a single bucket within a distribution.
    */
  trait Bucket {

    /**
      * Value at which the bucket starts
      */
    def value: Long

    /**
      * Number of times the value of this bucket was observed.
      */
    def frequency: Long
  }

  /**
    * Indicates the value below which a given percentage (or rank) of the entire distribution are.
    */
  trait Percentile {

    /**
      * Percentile rank for a given percentile. E.g. a rank of 99.05 expresses that 99.95% of all values on a
      * distribution are at or below the value of this percentile.
      */
    def rank: Double

    /**
      * The cutoff value for this percentile in a distribution. E.g. a value of 742ms with a rank of 95.0 express that
      * 95% of all values on a distribution are a 742ms or smaller.
      */
    def value: Long

    /**
      * Expresses how many values are at or under this percentile's rank. E.g. a percentile with value of 742ms and
      * count at rank of 500 expresses that there are 500 values at or under 742ms on a distribution.
      */
    def countAtRank: Long
  }

  /**
    * Merges two distributions into a new one, which includes the values from both distributions. The resulting
    * distribution will always have the dynamic range of the "left" distribution.
    */
  def merge(left: Distribution, right: Distribution): Distribution =
    merge(left, right, left.dynamicRange)

  /**
    * Merges two distributions into a new one, which includes the values from both distributions, adjusting the values
    * to the provided dynamic range if necessary.
    */
  def merge(left: Distribution, right: Distribution, dynamicRange: DynamicRange): Distribution = {
    val h = Histogram.Local.get(dynamicRange)
    left.bucketsIterator.foreach(b => h.recordValueWithCount(b.value, b.frequency))
    right.bucketsIterator.foreach(b => h.recordValueWithCount(b.value, b.frequency))
    h.snapshot(true)
  }

  /**
    * Tries to convert a distribution to the provided unit. Take into account that since Distributions are based on
    * buckets with integer boundaries, converting from greater to lower magnitudes (e.g. from seconds to milliseconds)
    * will always preserve precision, but the same is not true when converting the  way around (e.g. from milliseconds
    * to seconds) since the conversion could produce floating point result like which will always be rounded to the
    * nearest integer equal or greater than 1. For example, when converting a value of 3500 milliseconds to seconds,
    * the converted value of 3.2 seconds will be rounded down to 3 seconds and when converting a value of 300 milliseconds
    * to seconds, it will be rounded up to 1 (the smallest possible value in a histogram).
    *
    * If the distribution and target unit dimensions are not the same then a warning will be logged and the distribution
    * will be returned unchanged.
    */
  def convert(distribution: Distribution, unit: MeasurementUnit, toUnit: MeasurementUnit): Distribution =
    convert(distribution, unit, toUnit, distribution.dynamicRange)

  /**
    * Tries to convert the a distribution to the provided unit and dynamic range. Take into account that
    * since Distributions are based on buckets with integer boundaries, converting from greater to lower magnitudes
    * (e.g. from seconds to milliseconds) will always preserve precision, but the same is not true when converting the
    * way around (e.g. from milliseconds to seconds) since the conversion could produce floating point result like which
    * will always be rounded to the nearest integer equal or greater than 1. For example, when converting a value of
    * 3500 milliseconds to seconds, the converted value of 3.2 seconds will be rounded down to 3 seconds and when
    * converting a value of 300 milliseconds to seconds, it will be rounded up to 1 (the smallest possible value in a
    * histogram).
    *
    * If the distribution and target unit dimensions are not the same then a warning will be logged and the distribution
    * will be returned unchanged.
    */
  def convert(
    distribution: Distribution,
    unit: MeasurementUnit,
    toUnit: MeasurementUnit,
    toDynamicRange: DynamicRange
  ): Distribution = {

    if (unit == toUnit && distribution.dynamicRange == toDynamicRange)
      distribution
    else {
      val actualToUnit = if (unit.dimension == toUnit.dimension) toUnit
      else {
        _logger.warn(
          s"Can't convert distributions from the [${unit.dimension.name}] dimension into the " +
          s"[${toUnit.dimension.name}] dimension."
        )

        unit
      }

      if (unit == actualToUnit && distribution.dynamicRange == toDynamicRange)
        distribution
      else {
        val scaledHistogram = Histogram.Local.get(toDynamicRange)
        distribution.bucketsIterator.foreach(bucket => {
          val roundValue = Math.round(MeasurementUnit.convert(bucket.value, unit, toUnit))
          val convertedValue = if (roundValue == 0L) 1L else roundValue
          scaledHistogram.recordValueWithCount(convertedValue, bucket.frequency)
        })

        scaledHistogram.snapshot(true)
      }
    }
  }

  /**
    * Holds an immutable value distribution for a given range and precision. This implementation is closely coupled with
    * the HdrHistogram internal mechanics and uses several bits of its internal state to translate between the internal
    * zero run-length encoded version of the counts array in a histogram and the actual buckets and percentiles expected
    * to be seen by users.
    */
  private[kamon] class ZigZagCounts(
    val count: Long,
    minIndex: Int,
    maxIndex: Int,
    zigZagCounts: ByteBuffer,
    unitMagnitude: Int,
    subBucketHalfCount: Int,
    subBucketHalfCountMagnitude: Int,
    val dynamicRange: DynamicRange
  ) extends Distribution {

    val min: Long = if (count == 0) 0 else bucketValueAtIndex(minIndex)
    val max: Long = bucketValueAtIndex(maxIndex)
    lazy val sum: Long = bucketsIterator.foldLeft(0L)((a, b) => a + (b.value * b.frequency))

    def buckets: Seq[Bucket] = {
      val builder = Seq.newBuilder[Bucket]
      val allBuckets = bucketsIterator
      while (allBuckets.hasNext) {
        val b = allBuckets.next()
        builder += immutable.Bucket(b.value, b.frequency)
      }

      builder.result()
    }

    def bucketsIterator: Iterator[Bucket] = new Iterator[Bucket] {
      val buffer = zigZagCounts.duplicate()
      val bucket = mutable.Bucket(0, 0)
      var countsArrayIndex = 0

      def hasNext: Boolean =
        buffer.remaining() > 0

      def next(): Bucket = {
        val readLong = ZigZag.getLong(buffer)
        val frequency = if (readLong > 0) {
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

    def percentilesIterator: Iterator[Percentile] = new Iterator[Percentile] {
      val buckets = bucketsIterator
      val percentile = mutable.Percentile(0d, 0, 0)
      var countUnderQuantile = 0L

      def hasNext: Boolean =
        buckets.hasNext

      def next(): Percentile = {
        val bucket = buckets.next()
        countUnderQuantile += bucket.frequency
        percentile.rank = (countUnderQuantile * 100d) / ZigZagCounts.this.count
        percentile.countAtRank = countUnderQuantile
        percentile.value = bucket.value
        percentile
      }
    }

    def percentile(p: Double): Percentile = {
      val percentiles = percentilesIterator
      if (percentiles.hasNext) {
        var currentPercentile = percentiles.next()
        while (percentiles.hasNext && currentPercentile.rank < p) {
          currentPercentile = percentiles.next()
        }

        immutable.Percentile(p, currentPercentile.value, currentPercentile.countAtRank)

      } else immutable.Percentile(p, 0, 0)
    }

    def percentiles: Seq[Percentile] = {
      val builder = Seq.newBuilder[Percentile]
      val allPercentiles = percentilesIterator
      while (allPercentiles.hasNext) {
        val p = allPercentiles.next()
        builder += immutable.Percentile(p.rank, p.value, p.countAtRank)
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

    override def toString(): String = {
      s"Distribution{count=$count,min=$min,max=$max}"
    }
  }

  /** Mutable versions of buckets and percentiles, used to avoid allocations when returned iterators */
  private object mutable {
    case class Bucket(var value: Long, var frequency: Long) extends Distribution.Bucket
    case class Percentile(var rank: Double, var value: Long, var countAtRank: Long) extends Distribution.Percentile
  }

  /** Immutable versions of buckets and percentiles, used when fully materialized views are needed */
  private object immutable {
    case class Bucket(value: Long, frequency: Long) extends Distribution.Bucket
    case class Percentile(rank: Double, value: Long, countAtRank: Long) extends Distribution.Percentile
  }
}
