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

import kamon.metrics.{ Scale, MetricSnapshotLike }

package object newrelic {

  def toNewRelicMetric(scale: Scale)(name: String, scope: Option[String], snapshot: MetricSnapshotLike): NewRelic.Metric = {
    var total: Double = 0D
    var sumOfSquares: Double = 0D

    val measurementLevels = snapshot.measurements.iterator
    while (measurementLevels.hasNext) {
      val level = measurementLevels.next()
      val scaledValue = Scale.convert(snapshot.scale, scale, level.value)

      total += scaledValue * level.count
      sumOfSquares += (scaledValue * scaledValue) * level.count
    }

    val scaledMin = Scale.convert(snapshot.scale, scale, snapshot.min)
    val scaledMax = Scale.convert(snapshot.scale, scale, snapshot.max)

    NewRelic.Metric(name, scope, snapshot.numberOfMeasurements, total, total, scaledMin, scaledMax, sumOfSquares)
  }
}
