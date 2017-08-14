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


class DistributionAccumulator(dynamicRange: DynamicRange) {
  private val accumulatorHistogram = new HdrHistogram("metric-distribution-accumulator",
    tags = Map.empty, unit = MeasurementUnit.none, dynamicRange)

  def add(distribution: Distribution): Unit =
    distribution.bucketsIterator.foreach(b => accumulatorHistogram.record(b.value, b.frequency))

  def result(resetState: Boolean): Distribution =
    accumulatorHistogram.snapshot(resetState).distribution
}
