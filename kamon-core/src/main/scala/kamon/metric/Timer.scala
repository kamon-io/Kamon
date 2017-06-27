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

package kamon.metric

import kamon.Tags
import kamon.util.MeasurementUnit

trait Timer extends Histogram {
  def start(): StartedTimer
}

trait StartedTimer {
  def stop(): Unit
}

object StartedTimer {

  def createFor(histogram: Histogram): StartedTimer = new StartedTimer {
    var running = true
    val startTimestamp = System.nanoTime()

    override def stop(): Unit = synchronized {
      if(running) {
        histogram.record(System.nanoTime() - startTimestamp)
        running = false
      }
    }
  }
}

private[kamon] final class TimerImpl(val histogram: Histogram) extends Timer {

  override def unit: MeasurementUnit =
    histogram.unit

  override def dynamicRange: DynamicRange =
    histogram.dynamicRange

  override def record(value: Long): Unit =
    histogram.record(value)

  override def record(value: Long, times: Long): Unit =
    histogram.record(value, times)

  override def start(): StartedTimer =
    StartedTimer.createFor(histogram)
}


private[kamon] final class TimerMetricImpl(val underlyingHistogram: HistogramMetric) extends TimerMetric {

  override def unit: MeasurementUnit =
    underlyingHistogram.unit

  override def dynamicRange: DynamicRange =
    underlyingHistogram.dynamicRange

  override def record(value: Long): Unit =
    underlyingHistogram.record(value)

  override def record(value: Long, times: Long): Unit =
    underlyingHistogram.record(value, times)

  override def name: String =
    underlyingHistogram.name

  override def refine(tags: Tags): Timer =
    new TimerImpl(underlyingHistogram.refine(tags))

  override def refine(tags: (String, String)*): Timer =
    new TimerImpl(underlyingHistogram.refine(tags: _*))

  override def refine(tag: String, value: String): Timer =
    new TimerImpl(underlyingHistogram.refine(Map(tag -> value)))

  override def remove(tags: Tags): Boolean =
    underlyingHistogram.remove(tags)

  override def remove(tags: (String, String)*): Boolean =
    underlyingHistogram.remove(tags: _*)

  override def remove(tag: String, value: String): Boolean =
    underlyingHistogram.remove(tag, value)

  override def start(): StartedTimer =
    StartedTimer.createFor(underlyingHistogram)
}