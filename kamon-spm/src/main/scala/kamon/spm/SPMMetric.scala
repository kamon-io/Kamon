/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.spm

import kamon.metric.instrument._
import kamon.util.MilliTimestamp

case class SPMMetric(ts: MilliTimestamp, category: String, name: String, instrumentName: String, unitOfMeasurement: UnitOfMeasurement, snapshot: InstrumentSnapshot)

object SPMMetric {
  private def convert(unit: UnitOfMeasurement, value: Long): Long = unit match {
    case t: Time   ⇒ t.scale(Time.Milliseconds)(value).toLong
    case m: Memory ⇒ m.scale(Memory.Bytes)(value).toLong
    case _         ⇒ value
  }

  private def prefix(metric: SPMMetric): String = {
    s"${metric.ts.millis}\t${metric.category}-${metric.instrumentName}\t${metric.ts.millis}\t${metric.name}"
  }

  def format(metric: SPMMetric): String = metric match {
    case SPMMetric(_, _, _, _, unit, histo: Histogram#SnapshotType) ⇒ {
      val min = convert(unit, histo.min)
      val max = convert(unit, histo.max)
      val sum = convert(unit, histo.sum)
      s"${prefix(metric)}\t${min}\t${max}\t${sum}\t${histo.numberOfMeasurements}"
    }
    case SPMMetric(_, _, _, _, unit, counter: Counter#SnapshotType) ⇒ {
      s"${prefix(metric)}\t${counter.count}"
    }
  }
}
