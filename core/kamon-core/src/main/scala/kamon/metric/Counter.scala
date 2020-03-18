/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.metric

import java.util.function.{Consumer, Supplier}

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

/**
  * Instrument that tracks a monotonically increasing value.
  */
trait Counter extends Instrument[Counter, Metric.Settings.ForValueInstrument] {

  /**
    * Increments the counter by one.
    */
  def increment(): Counter

  /**
    * Increments the counter by the provided times.
    */
  def increment(times: Long): Counter
}

object Counter {

  private val _logger = LoggerFactory.getLogger(classOf[Counter])

  /**
    * Creates a counter consumer function that stores the delta between the current and previous value generated by the
    * provided supplier. This is specially useful when setting up auto-update actions on a counter from a cumulative
    * counter, since Kamon's counters only track the number of events since the last tick.
    */
  def delta(supplier: Supplier[Long]): Consumer[Counter] =
    delta(() => supplier.get())

  /**
    * Creates a counter consumer function that stores the delta between the current and previous value generated by the
    * provided supplier. This is specially useful when setting up auto-update actions on a counter from a cumulative
    * counter, since Kamon's counters only track the number of events since the last tick.
    */
  def delta(supplier: () => Long): Consumer[Counter] =
    new Consumer[Counter] {
      private var _previousValue = supplier()

      override def accept(counter: Counter): Unit = {
        val currentValue = supplier()
        val diff = currentValue - _previousValue
        _previousValue = currentValue

        if (diff >= 0)
          counter.increment(diff)
        else
          _logger.warn(s"Ignoring negative delta [$diff] when trying to update counter [${counter.metric.name},${counter.tags}]")
      }
    }

  /**
    * LongAdder-based counter implementation. LongAdder counters are safe to share across threads and provide superior
    * write performance over Atomic values in cases where the writes largely outweigh the reads, which is the common
    * case in Kamon counters (writing every time something needs to be tracked, reading roughly once per minute).
    */
  class LongAdder(val metric: BaseMetric[Counter, Metric.Settings.ForValueInstrument, Long], val tags: TagSet)
      extends Counter
      with Instrument.Snapshotting[Long]
      with BaseMetricAutoUpdate[Counter, Metric.Settings.ForValueInstrument, Long] {

    private val _adder = new kamon.jsr166.LongAdder()

    override def increment(): Counter = {
      _adder.increment()
      this
    }

    override def increment(times: Long): Counter = {
      if (times >= 0)
        _adder.add(times)
      else
        _logger.warn(s"Ignoring an attempt to decrease a counter by [$times] on [${metric.name},$tags]")

      this
    }

    override def snapshot(resetState: Boolean): Long =
      if (resetState)
        _adder.sumAndReset()
      else
        _adder.sum()

    override def baseMetric: BaseMetric[Counter, Metric.Settings.ForValueInstrument, Long] =
      metric
  }
}
