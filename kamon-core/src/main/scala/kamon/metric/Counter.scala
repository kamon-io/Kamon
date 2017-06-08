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

import java.util.concurrent.atomic.LongAdder

import com.typesafe.scalalogging.StrictLogging
import kamon.util.MeasurementUnit

trait Counter {
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
}

class LongAdderCounter(name: String, tags: Map[String, String], val measurementUnit: MeasurementUnit)
    extends SnapshotableCounter with StrictLogging {

  private val adder = new LongAdder()

  def increment(): Unit =
    adder.increment()

  def increment(times: Long): Unit = {
    if (times >= 0)
      adder.add(times)
    else
      logger.warn(s"Ignored attempt to decrement counter [$name]")
  }

  def snapshot(): MetricValue =
    MetricValue(name, tags, measurementUnit, adder.sumThenReset())
}
