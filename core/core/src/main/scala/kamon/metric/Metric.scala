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

package kamon.metric

import java.time.Duration
import java.util
import java.util.Collections
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import kamon.status.Status
import kamon.tag.TagSet

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
  * Describes a property of a system to be measured, and contains all the necessary information to create the actual
  * instrument instances used to measure and record said property. Practically, a Metric can be seen as a group
  * instruments that measure different dimensions (via tags) of the same property of the system.
  *
  * All instruments belonging to a given metric share the same settings and only differ from each other by the unique
  * combination of tags used to create them.
  *
  */
trait Metric[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings] extends Tagging[Inst] {

  /**
    * A unique identifier for this metric. Metric names typically will be namespaced, meaning that their name has a
    * structure similar to that of a package name that describes what component is generating the metric. For example,
    * metrics related to the JVM have the "jvm." prefix while metrics related to Akka Actors have the "akka.actor."
    * prefix.
    */
  def name: String

  /**
    * Short, concise and human readable explanation of what is being measured by a metric.
    */
  def description: String

  /**
    * Configuration settings that apply to all instruments of this metric.
    */
  def settings: Sett

  /**
    * Returns an instrument without tags for this metric.
    */
  def withoutTags(): Inst

  /**
    * Removes an instrument with the provided tags from a metric, if it exists. Returns true if the instrument existed
    * and was removed or false if no instrument was found with the provided tags.
    */
  def remove(tags: TagSet): Boolean
}

object Metric {

  /**
    * User-facing API for a Counter-based metric. All Kamon APIs returning a Counter-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Counter extends Metric[kamon.metric.Counter, Settings.ForValueInstrument]

  /**
    * User-facing API for a Gauge-based metric. All Kamon APIs returning a Gauge-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Gauge extends Metric[kamon.metric.Gauge, Settings.ForValueInstrument]

  /**
    * User-facing API for a Histogram-based metric. All Kamon APIs returning a Histogram-based metric to users should
    * always return this interface rather than internal representations.
    */
  trait Histogram extends Metric[kamon.metric.Histogram, Settings.ForDistributionInstrument]

  /**
    * User-facing API for a Timer-based metric. All Kamon APIs returning a Timer-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Timer extends Metric[kamon.metric.Timer, Settings.ForDistributionInstrument]

  /**
    * User-facing API for a Range Sampler-based metric. All Kamon APIs returning a Range Sampler-based metric to users
    * should always return this interface rather than internal representations.
    */
  trait RangeSampler extends Metric[kamon.metric.RangeSampler, Settings.ForDistributionInstrument]

  /**
    * Describes the minimum settings that should be provided to all metrics.
    */
  sealed trait Settings {

    /**
      * Measurement unit of the values tracked by a metric.
      */
    def unit: MeasurementUnit

    /**
      * Interval at which auto-update actions will be scheduled.
      */
    def autoUpdateInterval: Duration
  }

  object Settings {

    /**
      * Settings that apply to all metrics backed by instruments that produce a single value (e.g. counters and gauges).
      */
    case class ForValueInstrument(
      unit: MeasurementUnit,
      autoUpdateInterval: Duration
    ) extends Metric.Settings

    /**
      * Settings that apply to all metrics backed by instruments that produce value distributions (e.g. timers, range
      * samplers and, of course, histograms).
      */
    case class ForDistributionInstrument(
      unit: MeasurementUnit,
      autoUpdateInterval: Duration,
      dynamicRange: kamon.metric.DynamicRange
    ) extends Metric.Settings
  }

  /**
    * Exposes the required API to create metric snapshots. This API is not meant to be exposed to users.
    */
  private[kamon] trait Snapshotting[Sett <: Metric.Settings, Snap] {

    /**
      * Creates a snapshot for a metric. If the resetState flag is set to true, the internal state of all instruments
      * associated with this metric will be reset, if applicable.
      */
    def snapshot(resetState: Boolean): MetricSnapshot[Sett, Snap]
  }

  /**
    * Provides basic creation, lifecycle, tagging, scheduling and snapshotting operations for Kamon metrics. This base
    * metric keeps track of all instruments created for a given metric and ensures that every time an instrument is
    * requested from it, the instrument will be either created or retrieved if it was requested before, in a thread safe
    * manner.
    *
    * Any actions scheduled on an instrument will be cancelled if that instrument is removed from the metric.
    */

  type RichInstrument[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings, Snap] = Inst
    with Instrument.Snapshotting[Snap]
    with BaseMetricAutoUpdate[Inst, Sett, Snap]

  type InstrumentBuilder[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings, Snap] =
    (BaseMetric[Inst, Sett, Snap], TagSet) => RichInstrument[Inst, Sett, Snap]

  abstract class BaseMetric[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings, Snap](
    val name: String,
    val description: String,
    val settings: Sett,
    instrumentBuilder: InstrumentBuilder[Inst, Sett, Snap],
    scheduler: Option[ScheduledExecutorService]
  ) extends Metric[Inst, Sett] with Metric.Snapshotting[Sett, Snap] {

    @volatile private var _scheduler: Option[ScheduledExecutorService] = scheduler
    private val _instruments = TrieMap.empty[TagSet, InstrumentEntry]

    override def withTag(key: String, value: String): Inst =
      lookupInstrument(TagSet.of(key, value))

    override def withTag(key: String, value: Boolean): Inst =
      lookupInstrument(TagSet.of(key, value))

    override def withTag(key: String, value: Long): Inst =
      lookupInstrument(TagSet.of(key, value))

    override def withTags(tags: TagSet): Inst =
      lookupInstrument(tags)

    override def withoutTags(): Inst =
      lookupInstrument(TagSet.Empty)

    override def remove(tags: TagSet): Boolean = synchronized {
      _instruments.get(tags).map(entry => {
        entry.removeOnNextSnapshot = true
        entry.scheduledActions.dropWhile(sa => {
          sa.cancel(false)
          true
        })
      }).nonEmpty
    }

    override def snapshot(resetState: Boolean): MetricSnapshot[Sett, Snap] = synchronized {
      var instrumentSnapshots = List.empty[Instrument.Snapshot[Snap]]
      _instruments.foreach {
        case (tags, entry) =>
          val instrumentSnapshot = entry.instrument.snapshot(resetState)
          if (entry.removeOnNextSnapshot && resetState)
            _instruments.remove(tags)

          instrumentSnapshots = Instrument.Snapshot(tags, instrumentSnapshot) :: instrumentSnapshots
      }

      buildMetricSnapshot(this, instrumentSnapshots)
    }

    def bindScheduler(scheduler: ScheduledExecutorService): Unit = synchronized {
      _scheduler = Some(scheduler)
      _instruments.foreach {
        case (_, entry) =>
          entry.actions.foreach(action => {
            val (runnable, interval) = action
            entry.scheduledActions += scheduler.scheduleAtFixedRate(
              runnable,
              interval.toNanos,
              interval.toNanos,
              TimeUnit.NANOSECONDS
            )
          })
      }
    }

    def shutdown(): Unit = synchronized {
      _scheduler = None
      _instruments.foreach {
        case (_, entry) =>
          entry.scheduledActions.dropWhile(sa => {
            sa.cancel(false)
            true
          })
      }

      _instruments.clear()
    }

    def schedule(instrument: Inst, action: Runnable, interval: Duration): Any = synchronized {
      _instruments.get(instrument.tags).map { entry =>
        _scheduler match {
          case Some(scheduler) =>
            val scheduledAction =
              scheduler.scheduleAtFixedRate(action, interval.toNanos, interval.toNanos, TimeUnit.NANOSECONDS)
            entry.scheduledActions += (scheduledAction)
            entry.actions += ((action, interval))

          case None =>
            entry.actions += ((action, interval))
        }
      }
    }

    def status(): Status.Metric =
      Status.Metric(
        name,
        description,
        settings.unit,
        instrumentType,
        _instruments.keys.map(t => Status.Instrument(t)).toSeq
      )

    /** Used by the Status API only */
    protected def instrumentType: Instrument.Type

    protected def buildMetricSnapshot(
      metric: Metric[Inst, Sett],
      instruments: Seq[Instrument.Snapshot[Snap]]
    ): MetricSnapshot[Sett, Snap]

    private def lookupInstrument(tags: TagSet): Inst = {
      val entry =
        _instruments.atomicGetOrElseUpdate(tags, newInstrumentEntry(tags), cleanupStaleEntry, triggerDefaultSchedule)
      entry.removeOnNextSnapshot = false
      entry.instrument
    }

    private def newInstrumentEntry(tags: TagSet): InstrumentEntry = {
      val actions = Collections.synchronizedList(new util.ArrayList[(Runnable, Duration)]()).asScala
      val scheduledActions = Collections.synchronizedList(new util.ArrayList[ScheduledFuture[_]]()).asScala
      val instrument = instrumentBuilder(this, tags)

      new InstrumentEntry(instrument, actions, scheduledActions, false)
    }

    private def triggerDefaultSchedule(entry: InstrumentEntry): Unit =
      entry.instrument.defaultSchedule()

    private def cleanupStaleEntry(entry: InstrumentEntry): Unit =
      entry.scheduledActions.foreach(sa => sa.cancel(false))

    private class InstrumentEntry(
      val instrument: RichInstrument[Inst, Sett, Snap],
      val actions: mutable.Buffer[(Runnable, Duration)],
      val scheduledActions: mutable.Buffer[ScheduledFuture[_]],
      @volatile var removeOnNextSnapshot: Boolean
    )
  }

  /**
    * Handles registration of auto-update actions on a base metric.
    */
  trait BaseMetricAutoUpdate[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings, Snap] {
    self: Inst =>

    protected def baseMetric: BaseMetric[Inst, Sett, Snap]

    def defaultSchedule(): Unit = ()

    def autoUpdate(consumer: Inst => Unit, interval: Duration): Inst = {
      val instrument = this
      val action = new Runnable {
        override def run(): Unit = consumer(instrument)
      }

      baseMetric.schedule(instrument, action, interval)
      this
    }
  }
}
