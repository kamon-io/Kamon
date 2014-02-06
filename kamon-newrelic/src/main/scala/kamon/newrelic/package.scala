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

import kamon.metrics.MetricSnapshot

package object newrelic {

  def toNewRelicMetric(name: String, scope: Option[String], snapshot: MetricSnapshot): NewRelic.Metric = {
    var total: Double = 0D
    var sumOfSquares: Double = 0D

    val measurementLevels = snapshot.measurementLevels.iterator
    while (measurementLevels.hasNext) {
      val level = measurementLevels.next()

      // NewRelic metrics need to be scaled to seconds.
      val scaledValue = level.value / 1E9D

      total += scaledValue
      sumOfSquares += scaledValue * scaledValue
    }

    val scaledMin = snapshot.min / 1E9D
    val scaledMax = snapshot.max / 1E9D

    NewRelic.Metric(name, scope, snapshot.numberOfMeasurements, total, total, scaledMin, scaledMax, sumOfSquares)
  }
}
