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

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet
import org.HdrHistogram.{BaseAtomicHdrHistogram, BaseLocalHdrHistogram, HdrHistogramInternalState, ZigZag}
import org.slf4j.LoggerFactory

/**
  * Instrument that tracks the distribution of values within a configured range and precision.
  */
trait Histogram extends Instrument[Histogram, Metric.Settings.ForDistributionInstrument] {

  /**
    * Records one occurrence of the provided value. Keep in mind that the provided value will not be recorded as-is on
    * the Histogram but will be rather adjusted to a bucket within the configured precision. By default, all Kamon
    * histograms are configured to achieve up to 1% error margin across the entire range.
    */
  def record(value: Long): Histogram

  /**
    * Records several occurrences of the provided value. Keep in mind that the provided value will not be recorded as-is
    * on the Histogram but will be rather adjusted to a bucket within the configured precision. By default, all Kamon
    * histograms are configured to achieve up to 1% error margin across the entire range.
    */
  def record(value: Long, times: Long): Histogram

}

object Histogram {

  private val _logger = LoggerFactory.getLogger(classOf[Histogram])

  /**
    * Histogram implementation with thread safety guarantees. Instances of this class can be safely shared across
    * threads and updated concurrently.
    */
  class Atomic(
    val metric: BaseMetric[Histogram, Metric.Settings.ForDistributionInstrument, Distribution],
    val tags: TagSet,
    val dynamicRange: DynamicRange
  ) extends BaseAtomicHdrHistogram(dynamicRange) with Histogram
      with DistributionSnapshotBuilder
      with BaseMetricAutoUpdate[Histogram, Metric.Settings.ForDistributionInstrument, Distribution] {

    override def record(value: Long): Histogram = {
      try {
        recordValue(value)
      } catch {
        case e: ArrayIndexOutOfBoundsException =>
          val highestTrackableValue = getHighestTrackableValue()
          recordValue(highestTrackableValue)

          _logger.warn(
            s"Failed to record value [$value] on [${metric.name},${tags}] because the value is outside of the " +
            s"configured range. The recorded value was adjusted to the highest trackable value [$highestTrackableValue]. " +
            "You might need to change your dynamic range configuration for this metric",
            e
          )
      }

      this
    }

    override def record(value: Long, times: Long): Histogram = {
      try {
        recordValueWithCount(value, times)
      } catch {
        case e: ArrayIndexOutOfBoundsException =>
          val highestTrackableValue = getHighestTrackableValue()
          recordValueWithCount(highestTrackableValue, times)

          _logger.warn(
            s"Failed to record value [$value] on [${metric.name},${tags}] because the value is outside of the " +
            s"configured range. The recorded value was adjusted to the highest trackable value [$highestTrackableValue]. " +
            "You might need to change your dynamic range configuration for this metric",
            e
          )
      }

      this
    }

    override protected def baseMetric: BaseMetric[Histogram, Metric.Settings.ForDistributionInstrument, Distribution] =
      metric
  }

  /**
    * Creates a distribution snapshot directly from a counts array while it is being actively used.
    */
  private[kamon] trait DistributionSnapshotBuilder extends Instrument.Snapshotting[Distribution] {
    self: HdrHistogramInternalState =>

    def dynamicRange: DynamicRange

    def snapshot(resetState: Boolean): Distribution = {
      val buffer = DistributionSnapshotBuilder._tempSnapshotBuffer.get()
      val countsLimit = getCountsArraySize()
      var index = 0
      buffer.clear()

      var minIndex = Int.MaxValue
      var maxIndex = 0
      var totalCount = 0L

      while (index < countsLimit) {
        val countAtIndex = if (resetState) getAndSetFromCountsArray(index, 0L) else getFromCountsArray(index)

        var zerosCount = 0L
        if (countAtIndex == 0L) {
          index += 1
          zerosCount = 1
          while (index < countsLimit && getFromCountsArray(index) == 0L) {
            index += 1
            zerosCount += 1
          }
        }

        if (zerosCount > 0) {
          if (index < countsLimit)
            ZigZag.putLong(buffer, -zerosCount)
        } else {
          if (minIndex > index)
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

      val zigZagCountsBuffer = ByteBuffer.wrap(zigZagCounts).asReadOnlyBuffer()
      val distribution = new Distribution.ZigZagCounts(
        totalCount,
        minIndex,
        maxIndex,
        zigZagCountsBuffer,
        getUnitMagnitude(),
        getSubBucketHalfCount(),
        getSubBucketHalfCountMagnitude(),
        dynamicRange
      )

      distribution
    }
  }

  private object DistributionSnapshotBuilder {
    private val _tempSnapshotBuffer = new ThreadLocal[ByteBuffer] {
      override def initialValue(): ByteBuffer = ByteBuffer.allocate(33792)
    }
  }

  /**
    * Histogram implementation that can only be used local to a thread or with external synchronization. This is only
    * used to power aggregation of distributions given that it is a lot simpler to create histograms and dump all the
    * values on them rather than trying to manually aggregate the buckets and take into account adjustments on dynamic
    * range.
    */
  private[kamon] class Local(val dynamicRange: DynamicRange) extends BaseLocalHdrHistogram(dynamicRange)
      with Instrument.Snapshotting[Distribution] with DistributionSnapshotBuilder

  private[kamon] object Local {

    /** Keeps a thread-local cache of local histograms that can be reused when aggregating distributions. */
    private val _localHistograms = new ThreadLocal[collection.mutable.Map[DynamicRange, Histogram.Local]] {
      override def initialValue(): collection.mutable.Map[DynamicRange, Histogram.Local] =
        collection.mutable.Map.empty
    }

    /**
      * Creates or retrieves a local histogram for the provided dynamic range. In theory, it is safe to do this from the
      * memory usage perspective since distribution merging or converting (the use cases for a Local Histogram) are only
      * expected to happen on reporter threads and all reporters have their own dedicated thread so we wont have many
      * instances around.
      */
    def get(dynamicRange: DynamicRange): Histogram.Local = {
      val histogram = _localHistograms.get().getOrElseUpdate(dynamicRange, new Histogram.Local(dynamicRange))
      histogram.reset()
      histogram
    }
  }
}
