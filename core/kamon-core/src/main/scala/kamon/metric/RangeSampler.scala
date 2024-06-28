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

import java.lang.Math.abs
import java.util.concurrent.atomic.AtomicLong
import kamon.metric.Histogram.DistributionSnapshotBuilder
import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate, Settings}
import kamon.tag.TagSet
import org.HdrHistogram.BaseAtomicHdrHistogram
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

/**
  * Instrument that tracks the behavior of a variable that can increase and decrease over time. A range sampler keeps
  * track of the observed minimum and maximum values for the tracked variable and is constantly recording and resetting
  * those indicators to the current variable value to get the most descriptive possible approximation of the behavior
  * presented by the variable.
  *
  * When a snapshot is taken, this instrument generates a distribution of values observed which is guaranteed to have
  * the minimum and maximum values of the observed variable as well as several samples across time since the last
  * snapshot.
  */
trait RangeSampler extends Instrument[RangeSampler, Metric.Settings.ForDistributionInstrument] {

  /**
    * Increments the current value by one.
    */
  def increment(): RangeSampler

  /**
    * Increments the current value the provided number of times.
    */
  def increment(times: Long): RangeSampler

  /**
    * Decrements the current value by one.
    */
  def decrement(): RangeSampler

  /**
    * Decrements the current value the provided number of times.
    */
  def decrement(times: Long): RangeSampler

  /**
    * Triggers the sampling of the internal minimum, maximum and current value indicators.
    */
  def sample(): RangeSampler

  /**
    * Resets the minimum, maximum and current value indicators.
    */
  def resetDistribution(): RangeSampler

}

object RangeSampler {

  private val _logger = LoggerFactory.getLogger(classOf[RangeSampler])

  /**
    * Timer implementation with thread safety guarantees. Instances of this class can be safely shared across threads
    * and updated concurrently. This is, in fact, a close copy of the Histogram.Atomic implementation, modified to match
    * the Timer interface.
    */
  class Atomic(
    val metric: BaseMetric[RangeSampler, Metric.Settings.ForDistributionInstrument, Distribution],
    val tags: TagSet,
    val dynamicRange: DynamicRange
  ) extends BaseAtomicHdrHistogram(dynamicRange) with RangeSampler
      with Instrument.Snapshotting[Distribution] with DistributionSnapshotBuilder
      with BaseMetricAutoUpdate[RangeSampler, Metric.Settings.ForDistributionInstrument, Distribution] {

    private val _min = new AtomicLongMaxUpdater(new AtomicLong(0L))
    private val _max = new AtomicLongMaxUpdater(new AtomicLong(0L))
    private val _current = new AtomicLong()

    override def increment(): RangeSampler =
      increment(1)

    override def decrement(): RangeSampler =
      decrement(1)

    override def increment(times: Long): RangeSampler = {
      val currentValue = _current.addAndGet(times)
      _max.update(currentValue)
      this
    }

    override def resetDistribution(): RangeSampler = {
      _min.maxThenReset(0)
      _max.maxThenReset(0)
      _current.set(0)
      this
    }

    override def decrement(times: Long): RangeSampler = {
      val currentValue = _current.addAndGet(-times)
      _min.update(-currentValue)
      this
    }

    override def defaultSchedule(): Unit = {
      this.autoUpdate(_.sample(): Unit)
    }

    /** Triggers the sampling of the internal minimum, maximum and current value indicators. */
    override def sample(): RangeSampler = {
      try {
        val currentValue = {
          val value = _current.get()
          if (value <= 0) 0 else value
        }

        val currentMin = {
          val rawMin = _min.maxThenReset(-currentValue)
          if (rawMin >= 0)
            0
          else
            abs(rawMin)
        }

        val currentMax = _max.maxThenReset(currentValue)

        recordValue(currentValue)
        recordValue(currentMin)
        recordValue(currentMax)

      } catch {
        case _: ArrayIndexOutOfBoundsException =>
          _logger.warn(
            s"Failed to record value on [${metric.name},${tags}] because the value is outside of the " +
            "configured range. You might need to change your dynamic range configuration for this metric"
          )
      }

      this
    }

    override protected def baseMetric: BaseMetric[RangeSampler, Settings.ForDistributionInstrument, Distribution] =
      metric

    /**
      * Keeps track of the max value of an observed value using an AtomicLong as the underlying storage.
      */
    private class AtomicLongMaxUpdater(value: AtomicLong) {

      def update(newMax: Long): Unit = {
        @tailrec def compare(): Long = {
          val currentMax = value.get()
          if (newMax > currentMax) {
            if (!value.compareAndSet(currentMax, newMax)) {
              compare()
            } else newMax
          } else currentMax
        }
        compare()
      }

      def maxThenReset(newValue: Long): Long =
        value.getAndSet(newValue)
    }
  }
}
