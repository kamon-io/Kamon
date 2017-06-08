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

import kamon.util.MeasurementUnit


/**
  *
  * @param interval
  * @param metrics
  */
case class TickSnapshot(interval: Interval, metrics: MetricsSnapshot)

case class Interval(from: Long, to: Long)

case class MetricsSnapshot(
  histograms: Seq[MetricDistribution],
  minMaxCounters: Seq[MetricDistribution],
  gauges: Seq[MetricValue],
  counters: Seq[MetricValue]
)

/**
  * Snapshot for instruments that internally track a single value. Meant to be used for counters and gauges.
  *
  */
case class MetricValue(name: String, tags: Map[String, String], measurementUnit: MeasurementUnit, value: Long)

/**
  * Snapshot for instruments that internally the distribution of values in a defined dynamic range. Meant to be used
  * with histograms and min max counters.
  */
case class MetricDistribution(name: String, tags: Map[String, String],  measurementUnit: MeasurementUnit,
  dynamicRange: DynamicRange, distribution: Distribution)


trait Distribution {
  def buckets: Seq[Bucket]
  def bucketsIterator: Iterator[Bucket]

  def min: Long
  def max: Long
  def sum: Long
  def count: Long
  def percentile(p: Double): Percentile

  def percentiles: Seq[Percentile]
  def percentilesIterator: Iterator[Percentile]
}

trait Bucket {
  def value: Long
  def frequency: Long
}

trait Percentile {
  def quantile: Double
  def value: Long
  def countUnderQuantile: Long
}


trait DistributionSnapshotInstrument {
  private[kamon] def snapshot(): MetricDistribution
}

trait SingleValueSnapshotInstrument {
  private[kamon] def snapshot(): MetricValue
}

trait SnapshotableHistogram extends Histogram with DistributionSnapshotInstrument
trait SnapshotableMinMaxCounter extends MinMaxCounter with DistributionSnapshotInstrument
trait SnapshotableCounter extends Counter with SingleValueSnapshotInstrument
trait SnapshotableGauge extends Gauge with SingleValueSnapshotInstrument


