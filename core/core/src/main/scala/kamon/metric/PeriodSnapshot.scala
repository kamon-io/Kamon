/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package metric

import java.time.{Duration, Instant}

import kamon.tag.TagSet
import kamon.util.Clock

import scala.collection.mutable

/**
  * Contains immutable snapshots of all metrics and the values recorded on their instruments for a given period of time.
  */
case class PeriodSnapshot(
  from: Instant,
  to: Instant,
  counters: Seq[MetricSnapshot.Values[Long]],
  gauges: Seq[MetricSnapshot.Values[Double]],
  histograms: Seq[MetricSnapshot.Distributions],
  timers: Seq[MetricSnapshot.Distributions],
  rangeSamplers: Seq[MetricSnapshot.Distributions]
)

object PeriodSnapshot {

  /**
    * Creates a new PeriodSnapshot accumulator using the default Kamon clock as time source.
    */
  def accumulator(period: Duration, margin: Duration): PeriodSnapshot.Accumulator =
    new PeriodSnapshot.Accumulator(period, margin, Duration.ofDays(365))

  /**
    * Creates a new PeriodSnapshot accumulator that removes stale instruments and metrics after the provided stale
    * period.
    */
  def accumulator(period: Duration, margin: Duration, stalePeriod: Duration): PeriodSnapshot.Accumulator =
    new PeriodSnapshot.Accumulator(period, margin, stalePeriod)

  /**
    * Accumulates PeriodSnapshot instances over the specified period of time and produces a single PeriodSnapshot that
    * merges all metrics and instruments accumulated during that period. This class contains mutable state, is not
    * thread safe and assumes that all snapshots passed to the `accumulate(...)` function are ordered in time.
    *
    * The typical use of this class would be when writing metric reporters that have to report data at a specific
    * interval and want to protect from users configuring a more frequent metrics tick interval. Example:
    *
    * {{{
    * class Reporter extends MetricsReporter {
    *   val accumulator = PeriodSnapshot.accumulator(Duration.ofSeconds(60), Duration.ofSeconds(1))
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
    * The margin time is used to determine how close the current accumulated interval can be to the expected interval
    * and still get reported. In the example above a accumulated period of 59.6 seconds has a margin to 60 seconds of
    * 0.4 seconds, thus, getting reported immediately instead of waiting for the next snapshot.
    *
    * A detail of what has been accumulated can be obtained by calling the `.peek()`ing into the accumulator.
    *
    * @param period for how long to accumulate snapshots
    * @param margin error margin for expected reporting period
    * @param stalePeriod the period of time an instrument should be missing from the incoming snapshots to consider it
    *                    stale and remove it.
    */
  class Accumulator(period: Duration, margin: Duration, stalePeriod: Duration) {
    private val _counters: ValueMetricStorage[Long] = mutable.Map.empty
    private val _gauges: ValueMetricStorage[Double] = mutable.Map.empty
    private val _histograms: DistributionMetricStorage = mutable.Map.empty
    private val _timers: DistributionMetricStorage = mutable.Map.empty
    private val _rangeSamplers: DistributionMetricStorage = mutable.Map.empty

    private var _nextTick: Instant = Instant.EPOCH
    private var _accumulatingFrom: Option[Instant] = None

    def add(periodSnapshot: PeriodSnapshot): Option[PeriodSnapshot] = {

      // Initialize the next tick based on incoming snapshots.
      if (_nextTick == Instant.EPOCH)
        _nextTick = Clock.nextAlignedInstant(periodSnapshot.to, period)

      // short-circuit if there is no need to accumulate (e.g. when metrics tick-interval is the same as duration or the
      // snapshots have a longer period than the duration).
      if (isSameDurationAsTickInterval() || (isAroundNextTick(periodSnapshot.to) && _accumulatingFrom.isEmpty))
        Some(periodSnapshot)
      else {
        if (_accumulatingFrom.isEmpty)
          _accumulatingFrom = Some(periodSnapshot.from)

        periodSnapshot.counters.foreach(c => accumulateValue(periodSnapshot.to, _counters, c))
        periodSnapshot.gauges.foreach(g => keepLastValue(periodSnapshot.to, _gauges, g))
        periodSnapshot.histograms.foreach(h => accumulateDistribution(periodSnapshot.to, _histograms, h))
        periodSnapshot.timers.foreach(t => accumulateDistribution(periodSnapshot.to, _timers, t))
        periodSnapshot.rangeSamplers.foreach(rs => accumulateDistribution(periodSnapshot.to, _rangeSamplers, rs))

        for (from <- _accumulatingFrom if isAroundNextTick(periodSnapshot.to)) yield {
          val accumulatedPeriodSnapshot = buildPeriodSnapshot(from, periodSnapshot.to, resetState = true)
          _nextTick = Clock.nextAlignedInstant(_nextTick, period)
          _accumulatingFrom = None
          clearAccumulatedData()

          accumulatedPeriodSnapshot
        }
      }
    }

    def peek(): PeriodSnapshot = {
      buildPeriodSnapshot(_accumulatingFrom.getOrElse(_nextTick), _nextTick, resetState = false)
    }

    private def isAroundNextTick(instant: Instant): Boolean = {
      Duration.between(instant, _nextTick.minus(margin)).toMillis() <= 0
    }

    private def isSameDurationAsTickInterval(): Boolean = {
      Kamon.config().getDuration("kamon.metric.tick-interval") == period
    }

    private def buildPeriodSnapshot(from: Instant, to: Instant, resetState: Boolean): PeriodSnapshot = {
      cleanStaleEntries(to)

      val snapshot = PeriodSnapshot(
        from,
        to,
        counters = valueSnapshots(_counters),
        gauges = valueSnapshots(_gauges),
        histograms = distributionSnapshots(_histograms),
        timers = distributionSnapshots(_timers),
        rangeSamplers = distributionSnapshots(_rangeSamplers)
      )

      if (resetState)
        clearAccumulatedData()

      snapshot
    }

    private def valueSnapshots[T](storage: ValueMetricStorage[T]): Seq[MetricSnapshot.Values[T]] = {
      var metrics = List.empty[MetricSnapshot.Values[T]]
      storage.foreach {
        case (_, metricEntry) =>
          val snapshot = MetricSnapshot.ofValues(
            metricEntry.snapshot.name,
            metricEntry.snapshot.description,
            metricEntry.snapshot.settings,
            metricEntry.instruments.map { case (tags, entry) => Instrument.Snapshot(tags, entry.snapshot) } toSeq
          )

          metrics = snapshot :: metrics
      }

      metrics
    }

    private def distributionSnapshots(storage: DistributionMetricStorage): Seq[MetricSnapshot.Distributions] = {
      var metrics = List.empty[MetricSnapshot.Distributions]
      storage.foreach {
        case (_, metricEntry) =>
          val snapshot = MetricSnapshot.ofDistributions(
            metricEntry.snapshot.name,
            metricEntry.snapshot.description,
            metricEntry.snapshot.settings,
            metricEntry.instruments.map { case (tags, entry) => Instrument.Snapshot(tags, entry.snapshot) } toSeq
          )

          metrics = snapshot :: metrics
      }

      metrics
    }

    private def accumulateValue(
      currentInstant: Instant,
      storage: ValueMetricStorage[Long],
      current: MetricSnapshot.Values[Long]
    ): Unit =
      accumulate(currentInstant, storage, current)(_ + _)

    private def keepLastValue(
      currentInstant: Instant,
      storage: ValueMetricStorage[Double],
      current: MetricSnapshot.Values[Double]
    ): Unit =
      accumulate(currentInstant, storage, current)((c, _) => c)

    private def accumulateDistribution(
      currentInstant: Instant,
      storage: DistributionMetricStorage,
      current: MetricSnapshot.Distributions
    ): Unit =
      accumulate(currentInstant, storage, current)(Distribution.merge)

    private def accumulate[IS, MS <: Metric.Settings](
      currentInstant: Instant,
      storage: mutable.Map[String, MetricEntry[MS, IS]],
      current: MetricSnapshot[MS, IS]
    )(combine: (IS, IS) => IS): Unit = {

      storage.get(current.name) match {
        case None =>
          // If the metric is not present just register it
          val instruments = mutable.Map.newBuilder[TagSet, InstrumentEntry[IS]]
          current.instruments.foreach {
            case Instrument.Snapshot(tags, snapshot) =>
              instruments += (tags -> InstrumentEntry(snapshot, currentInstant))
          }

          storage.put(current.name, MetricEntry[MS, IS](current, instruments.result()))

        case Some(metricEntry) =>
          // If the metric was already registered, the values of all instruments must be aggregated with any
          // previously registered values
          current.instruments.foreach {
            case Instrument.Snapshot(tags, instrumentSnapshot) =>
              metricEntry.instruments.get(tags) match {
                case Some(instrumentEntry) =>
                  instrumentEntry.snapshot = combine(instrumentSnapshot, instrumentEntry.snapshot)
                  instrumentEntry.lastSeen = currentInstant

                case None =>
                  metricEntry.instruments.put(tags, InstrumentEntry(instrumentSnapshot, currentInstant))
              }
          }
      }
    }

    private def cleanStaleEntries(currentInstant: Instant): Unit = {
      val cutoff = currentInstant.minus(stalePeriod)

      def clean[A <: Metric.Settings, B](store: mutable.Map[String, MetricEntry[A, B]]): Unit = {
        // Removes all stale instruments
        store.foreach { case (_, metricEntry) =>
          metricEntry.instruments.retain {
            case (_, instrumentEntry) => instrumentEntry.lastSeen.isAfter(cutoff)
          }
        }

        // Removes all metrics that don't have any instruments left
        store.retain { case (_, metricEntry) => metricEntry.instruments.nonEmpty }
      }

      clean(_counters)
      clean(_gauges)
      clean(_histograms)
      clean(_timers)
      clean(_rangeSamplers)
    }

    private def clearAccumulatedData(): Unit = {
      _counters.clear()
      _gauges.clear()
      _histograms.clear()
      _timers.clear()
      _rangeSamplers.clear()
    }

    private type ValueMetricStorage[T] = mutable.Map[String, MetricEntry[Metric.Settings.ForValueInstrument, T]]
    private type DistributionMetricStorage =
      mutable.Map[String, MetricEntry[Metric.Settings.ForDistributionInstrument, Distribution]]

    private case class MetricEntry[Sett <: Metric.Settings, Snap](
      snapshot: MetricSnapshot[Sett, Snap],
      instruments: mutable.Map[TagSet, InstrumentEntry[Snap]]
    )

    private case class InstrumentEntry[Snap](
      var snapshot: Snap,
      var lastSeen: Instant
    )
  }
}
