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

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit

import kamon.metric.Histogram.DistributionSnapshotBuilder
import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate, Settings}
import kamon.tag.TagSet
import kamon.util.Clock
import org.HdrHistogram.BaseAtomicHdrHistogram
import org.slf4j.LoggerFactory

/**
  * Instrument that tracks the distribution of latency values within a configured range and precision. Timers are just a
  * special case of histograms that provide special APIs dedicated to recording latency measurements.
  */
trait Timer extends Instrument[Timer, Metric.Settings.ForDistributionInstrument] {

  /**
    * Starts counting elapsed time from the instant this method is called and until the returned Timer.Started instance
    * is stopped.
    */
  def start(): Timer.Started

  /**
    * Records one occurrence of the provided latency value. Keep in mind that the provided value will not be recorded
    * as-is on the resulting Histogram but will be rather adjusted to a bucket within the configured precision. By
    * default, all Kamon histograms are configured to achieve up to 1% error margin across the entire range.
    */
  def record(nanos: Long): Timer

  /**
    * Records one occurrence of the provided duration. Keep in mind that the provided value will not be recorded
    * as-is on the resulting Histogram but will be rather adjusted to a bucket within the configured precision. By
    * default, all Kamon histograms are configured to achieve up to 1% error margin across the entire range.
    */
  def record(duration: Duration): Timer

  /**
    * Records one occurrence of the provided duration. Keep in mind that the provided value will not be recorded
    * as-is on the resulting Histogram but will be rather adjusted to a bucket within the configured precision. By
    * default, all Kamon histograms are configured to achieve up to 1% error margin across the entire range.
    */
  def record(elapsed: Long, unit: TimeUnit): Timer

}

object Timer {

  private val _logger = LoggerFactory.getLogger(classOf[Timer])

  /**
    * Measures the elapsed time between the instant when a timer is started and the instant when it is stopped.
    */
  trait Started extends Tagging[Started] {

    /**
      * Stops the timer and record the elapsed time since it was started.
      */
    def stop(): Unit

  }

  /**
    * Timer implementation with thread safety guarantees. Instances of this class can be safely shared across threads
    * and updated concurrently. This is, in fact, a close copy of the Histogram.Atomic implementation, modified to match
    * the Timer interface.
    */
  class Atomic(
    val metric: BaseMetric[Timer, Metric.Settings.ForDistributionInstrument, Distribution],
    val tags: TagSet,
    val dynamicRange: DynamicRange,
    clock: Clock
  ) extends BaseAtomicHdrHistogram(dynamicRange) with Timer
      with Instrument.Snapshotting[Distribution] with DistributionSnapshotBuilder
      with BaseMetricAutoUpdate[Timer, Metric.Settings.ForDistributionInstrument, Distribution] {

    /** Starts a timer that will record the elapsed time between the start and stop instants */
    override def start(): Started =
      new TaggableStartedTimer(clock.instant(), clock, this)

    /** Records a value on the underlying histogram, handling the case of overflowing the dynamic range */
    override def record(nanos: Long): Timer = {
      try {
        recordValue(nanos)
      } catch {
        case _: ArrayIndexOutOfBoundsException =>
          val highestTrackableValue = getHighestTrackableValue()
          recordValue(highestTrackableValue)

          _logger.warn(
            s"Failed to record value [$nanos] on [${metric.name},${tags}] because the value is outside of the " +
            s"configured range. The recorded value was adjusted to the highest trackable value [$highestTrackableValue]. " +
            "You might need to change your dynamic range configuration for this metric"
          )
      }

      this
    }

    /** Records a specified duration, translated to nanoseconds */
    override def record(duration: Duration): Timer =
      record(duration.toNanos)

    /** Records an elapsed time expressed on the provided time unit */
    override def record(elapsed: Long, unit: TimeUnit): Timer =
      record(unit.toNanos(elapsed))

    override protected def baseMetric: BaseMetric[Timer, Settings.ForDistributionInstrument, Distribution] =
      metric
  }

  /**
    * Started timer implementation that allows applying tags before the timer is stopped.
    */
  private class TaggableStartedTimer(startedAt: Instant, clock: Clock, instrument: Timer) extends Timer.Started {
    private var stopped = false

    override def withTag(key: String, value: String): Timer.Started =
      new TaggableStartedTimer(startedAt, clock, instrument.withTag(key, value))

    override def withTag(key: String, value: Boolean): Timer.Started =
      new TaggableStartedTimer(startedAt, clock, instrument.withTag(key, value))

    override def withTag(key: String, value: Long): Timer.Started =
      new TaggableStartedTimer(startedAt, clock, instrument.withTag(key, value))

    override def withTags(tags: TagSet): Timer.Started =
      new TaggableStartedTimer(startedAt, clock, instrument.withTags(tags))

    override def stop(): Unit = synchronized {
      if (!stopped) {
        instrument.record(clock.nanosSince(startedAt))
        stopped = true
      }
    }
  }
}
