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




trait Metric[Inst, Sett <: Metric.Settings] extends Tagging[Inst] {
  def name: String
  def description: String
  def settings: Sett

  /**
    * Returns an instrument without tags for this metric.
    */
  def withoutTags(): Inst

  /**
    * Removes an instrument with the provided tags from a metric, if it exists.
    *
    * @return True if the instrument existed and was removed or false if no instrument was found with the provided tags.
    */
  def remove(tags: TagSet): Boolean
}



object Metric {

  /**
    * User-facing API for a counter-based metric. All Kamon APIs returning a counter-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Counter extends Metric[kamon.metric.Counter, Settings.ValueInstrument]

  /**
    * User-facing API for a gauge-based metric. All Kamon APIs returning a gauge-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Gauge extends Metric[kamon.metric.Gauge, Settings.ValueInstrument]

  /**
    * User-facing API for a histogram-based metric. All Kamon APIs returning a histogram-based metric to users should
    * always return this interface rather than internal representations.
    */
  trait Histogram extends Metric[kamon.metric.Histogram, Settings.DistributionInstrument]

  /**
    * User-facing API for a timer-based metric. All Kamon APIs returning a timer-based metric to users should always
    * return this interface rather than internal representations.
    */
  trait Timer extends Metric[kamon.metric.Timer, Settings.DistributionInstrument]

  /**
    * User-facing API for a range sampler-based metric. All Kamon APIs returning a range sampler-based metric to users
    * should always return this interface rather than internal representations.
    */
  trait RangeSampler extends Metric[kamon.metric.RangeSampler, Settings.DistributionInstrument]


  /**
    * Describes the minimum settings that should be provided to all metrics.
    */
  trait Settings {

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
    case class ValueInstrument (
      unit: MeasurementUnit,
      autoUpdateInterval: Duration
    ) extends Metric.Settings


    /**
      * Settings that apply to all metrics backed by instruments that produce value distributions (e.g. timers, range
      * samplers and, of course, histograms).
      */
    case class DistributionInstrument (
      unit: MeasurementUnit,
      autoUpdateInterval: Duration,
      dynamicRange: kamon.metric.DynamicRange
    ) extends Metric.Settings
  }


  /**
    * Exposes the required API to create metric snapshots. This API is not meant to be exposed to users.
    */
  trait Snapshotting[Sett <: Metric.Settings, Snap] {

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
  abstract class BaseMetric[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings, Snap] (
      val name: String,
      val description: String,
      val settings: Sett,
      instrumentBuilder: (BaseMetric[Inst, Sett, Snap], TagSet) => Inst with Instrument.Snapshotting[Snap],
      scheduler: ScheduledExecutorService) extends Metric[Inst, Sett] with Metric.Snapshotting[Sett, Snap] {

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
      val instrumentSnapshots = Map.newBuilder[TagSet, Snap]
      _instruments.foreach {
        case (tags, entry) =>
          val instrumentSnapshot = entry.instrument.snapshot(resetState)
          if(entry.removeOnNextSnapshot && resetState)
            _instruments.remove(tags)

          instrumentSnapshots += (tags -> instrumentSnapshot)
      }

      buildMetricSnapshot(this, instrumentSnapshots.result())
    }

    def schedule(instrument: Inst, action: Runnable, interval: Duration): Any = synchronized {
      _instruments.get(instrument.tags).map { entry =>
        val scheduledAction = scheduler.scheduleAtFixedRate(action, interval.toNanos, interval.toNanos, TimeUnit.NANOSECONDS)
        entry.scheduledActions += scheduledAction
      }
    }

    def status(): Status.Metric =
      Status.Metric (
        name,
        description,
        settings.unit,
        instrumentType,
        _instruments.keys.map(t => Status.Instrument(t)).toSeq
      )

    /** Used by the Status API only */
    protected def instrumentType: Instrument.Type

    protected def buildMetricSnapshot(metric: Metric[Inst, Sett], instruments: Map[TagSet, Snap]): MetricSnapshot[Sett, Snap]

    private def lookupInstrument(tags: TagSet): Inst = {
      val entry = _instruments.atomicGetOrElseUpdate(tags, newInstrumentEntry(tags))
      entry.removeOnNextSnapshot = false
      entry.instrument
    }

    private def newInstrumentEntry(tags: TagSet): InstrumentEntry =
      new InstrumentEntry(instrumentBuilder(this, tags), Collections.synchronizedList(new util.ArrayList[ScheduledFuture[_]]()).asScala, false)

    private class InstrumentEntry (
      val instrument: Inst with Instrument.Snapshotting[Snap],
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