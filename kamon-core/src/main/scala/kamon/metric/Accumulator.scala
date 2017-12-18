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

import java.time.{Duration, Instant}

import kamon.{Kamon, Tags}
import kamon.metric.PeriodSnapshotAccumulator.{MetricDistributionKey, MetricValueKey}
import kamon.util.Clock

import scala.collection.mutable


class DistributionAccumulator(dynamicRange: DynamicRange) {
  private val accumulatorHistogram = new HdrHistogram("metric-distribution-accumulator",
    tags = Map.empty, unit = MeasurementUnit.none, dynamicRange)

  def add(distribution: Distribution): Unit =
    distribution.bucketsIterator.foreach(b => accumulatorHistogram.record(b.value, b.frequency))

  def result(resetState: Boolean): Distribution =
    accumulatorHistogram.snapshot(resetState).distribution
}

/**
  * Merges snapshots over the specified duration and produces a snapshot with all merged metrics provided to it within
  * the period. This class is mutable, not thread safe and assumes that all snapshots passed to the `accumulate(...)`
  * function are ordered in time.
  *
  * The typical use of this class would be when writing metric reporters that have to report data at a specific interval
  * and wants to protect from users configuring a more frequent metrics tick interval. Example:
  *
  * {{{
  * class Reporter extends MetricsReporter {
  *   val accumulator = new PeriodSnapshotAccumulator(Duration.ofSeconds(60), Duration.ofSeconds(1))
  *
  *   def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
  *     accumulator.add(snapshot).foreach(accumulatedSnapshot => {
  *       // Process your snapshot here, will only be called when the expected period has passed.
  *     }
  *   }
  *
  *   ...
  * }
  * }}}
  *
  * The margin time is used to determine how close the current accumulated interval can to be to the expected interval
  * and still get reported. In the example above a accumulated period of 59.6 seconds has a margin to 60 seconds of
  * 0.4 seconds, thus, getting reported immediately instead of waiting for the next snapshot.
  *
  * A detail of what has been accumulated by calling the `.peek()` function.
  *
  * @param duration for how long to accumulate snapshots
  * @param margin error margin for expected reporting period
  */
class PeriodSnapshotAccumulator(duration: Duration, margin: Duration) {
  private val counters = mutable.Map[MetricValueKey, Long]()
  private val gauges = mutable.Map[MetricValueKey, Long]()
  private val histograms = mutable.Map[MetricDistributionKey, DistributionAccumulator]()
  private val rangeSamplers = mutable.Map[MetricDistributionKey, DistributionAccumulator]()

  private var nextTick: Instant = Instant.EPOCH
  private var accumulatingFrom: Option[Instant] = None

  def add(periodSnapshot: PeriodSnapshot): Option[PeriodSnapshot] = {
    // Initialize the next tick based on incoming snapshots.
    if(nextTick == Instant.EPOCH)
      nextTick = Clock.nextTick(periodSnapshot.to, duration)

    // short-circuit if there is no need to accumulate (e.g. when metrics tick-interval is the same as duration or the
    // snapshots have a longer period than the duration).
    if(isSameDurationAsTickInterval() || (isAroundNextTick(periodSnapshot.to) && accumulatingFrom.isEmpty)) Some(periodSnapshot) else {
      if (accumulatingFrom.isEmpty)
        accumulatingFrom = Some(periodSnapshot.from)

      periodSnapshot.metrics.counters.foreach(c => accumulateValue(counters, c))
      periodSnapshot.metrics.gauges.foreach(g => replaceValue(gauges, g))
      periodSnapshot.metrics.histograms.foreach(h => accumulateDistribution(histograms, h))
      periodSnapshot.metrics.rangeSamplers.foreach(rs => accumulateDistribution(rangeSamplers, rs))

      for(from <- accumulatingFrom if isAroundNextTick(periodSnapshot.to)) yield {
        val accumulatedPeriodSnapshot = buildPeriodSnapshot(from, periodSnapshot.to, resetState = true)
        nextTick = Clock.nextTick(nextTick, duration)
        accumulatingFrom = None
        clearAccumulatedData()

        accumulatedPeriodSnapshot
      }
    }
  }

  def peek(): PeriodSnapshot = {
    buildPeriodSnapshot(accumulatingFrom.getOrElse(nextTick), nextTick, resetState = false)
  }

  private def isAroundNextTick(instant: Instant): Boolean = {
    Duration.between(instant, nextTick.minus(margin)).toMillis() <= 0
  }

  private def isSameDurationAsTickInterval(): Boolean = {
    Kamon.config().getDuration("kamon.metric.tick-interval").equals(duration)
  }

  private def buildPeriodSnapshot(from: Instant, to: Instant, resetState: Boolean): PeriodSnapshot = {
    val metrics = MetricsSnapshot(
      histograms = histograms.map(createDistributionSnapshot(resetState)).toSeq,
      rangeSamplers = rangeSamplers.map(createDistributionSnapshot(resetState)).toSeq,
      gauges = gauges.map(createValueSnapshot(resetState)).toSeq,
      counters = counters.map(createValueSnapshot(resetState)).toSeq
    )

    PeriodSnapshot(from, to, metrics)
  }

  private def accumulateValue(cache: mutable.Map[MetricValueKey, Long], metric: MetricValue): Unit = {
    val key = MetricValueKey(metric.name, metric.tags, metric.unit)
    cache.get(key).map(previousValue => {
      cache.put(key, metric.value + previousValue)
    }).orElse {
      cache.put(key, metric.value)
    }
  }

  private def replaceValue(cache: mutable.Map[MetricValueKey, Long], metric: MetricValue): Unit = {
    val key = MetricValueKey(metric.name, metric.tags, metric.unit)
    cache.put(key, metric.value)
  }

  private def createValueSnapshot(reset: Boolean)(pair: (MetricValueKey, Long)): MetricValue = {
    val (key, value) = pair
    MetricValue(key.name, key.tags, key.unit, value)
  }

  private def createDistributionSnapshot(resetState: Boolean)(pair: (MetricDistributionKey, DistributionAccumulator)): MetricDistribution = {
    val (key, value) = pair
    MetricDistribution(key.name, key.tags, key.unit, key.dynamicRange, value.result(resetState))
  }

  private def accumulateDistribution(cache: mutable.Map[MetricDistributionKey, DistributionAccumulator], metric: MetricDistribution): Unit = {
    val key = MetricDistributionKey(metric.name, metric.tags, metric.unit, metric.dynamicRange)
    cache.get(key).map(previousValue => {
      previousValue.add(metric.distribution)
    }).orElse {
      val distributionAccumulator = new DistributionAccumulator(key.dynamicRange)
      distributionAccumulator.add(metric.distribution)
      cache.put(key, distributionAccumulator)
    }
  }

  private def clearAccumulatedData(): Unit = {
    histograms.clear()
    rangeSamplers.clear()
    counters.clear()
    gauges.clear()
  }
}

object PeriodSnapshotAccumulator {
  case class MetricValueKey(name: String, tags: Tags, unit: MeasurementUnit)
  case class MetricDistributionKey(name: String, tags: Tags, unit: MeasurementUnit,  dynamicRange: DynamicRange)
}
