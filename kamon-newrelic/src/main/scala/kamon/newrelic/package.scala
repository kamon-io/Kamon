/*=========================================================================================
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
 *
 */

package kamon

import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metric.{ MetricSnapshot, Scale }

package object newrelic {

  def toNewRelicMetric(scale: Scale)(name: String, scope: Option[String], snapshot: MetricSnapshot): NewRelic.Metric = {
    snapshot match {
      case hs: Histogram.Snapshot ⇒
        var total: Double = 0D
        var sumOfSquares: Double = 0D
        val scaledMin = Scale.convert(hs.scale, scale, hs.min)
        val scaledMax = Scale.convert(hs.scale, scale, hs.max)

        hs.recordsIterator.foreach { record ⇒
          val scaledValue = Scale.convert(hs.scale, scale, record.level)

          total += scaledValue * record.count
          sumOfSquares += (scaledValue * scaledValue) * record.count
        }

        NewRelic.Metric(name, scope, hs.numberOfMeasurements, total, total, scaledMin, scaledMax, sumOfSquares)

      case cs: Counter.Snapshot ⇒
        NewRelic.Metric(name, scope, cs.count, cs.count, cs.count, 0, cs.count, cs.count * cs.count)
    }
  }
}
