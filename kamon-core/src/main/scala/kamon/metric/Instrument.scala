package kamon.metric

import java.time.Duration
import java.util.function.Consumer

import kamon.tag.TagSet

trait Instrument[Inst, Sett <: Metric.Settings] extends Tagging[Inst] {
  def metric: Metric[Inst, Sett]
  def tags: TagSet

  def remove(): Boolean =
    metric.remove(tags)

  override def withTag(key: String, value: String): Inst =
    metric.withTags(tags.and(key, value))

  override def withTag(key: String, value: Boolean): Inst =
    metric.withTags(tags.and(key, value))

  override def withTag(key: String, value: Long): Inst =
    metric.withTags(tags.and(key, value))

  override def withTags(tags: TagSet): Inst =
    metric.withTags(tags.and(tags))



  /**
    * Schedules a call to the provided consumer with a reference to this histogram as parameter, overriding the metric's
    * auto-update interval.
    */
  def autoUpdate(consumer: Inst => Unit, interval: Duration): Inst

  /**
    * Schedules a call to the provided consumer with a reference to this histogram as parameter. The schedule uses the
    * default auto-update interval. See the `kamon.metric.instrument-factory` configuration settings for more details.
    */
  def autoUpdate(consumer: Consumer[Inst]): Inst =
    autoUpdate(h => consumer.accept(h), metric.settings.autoUpdateInterval)

  /**
    * Schedules a call to the provided consumer with a reference to this histogram as parameter, overriding the metric's
    * auto-update interval.
    */
  def autoUpdate(consumer: Consumer[Inst], interval: Duration): Inst =
    autoUpdate(h => consumer.accept(h), interval)

  /**
    * Schedules a call to the provided consumer with a reference to this histogram as parameter. The schedule uses the
    * default auto-update interval. See the `kamon.metric.instrument-factory` configuration settings for more details.
    */
  def autoUpdate(consumer: Inst => Unit): Inst =
    autoUpdate(consumer, metric.settings.autoUpdateInterval)

}

object Instrument {

  /**
    * Exposes the required API to create instrument snapshots snapshots. This API is not meant to be exposed to users.
    */
  trait Snapshotting[Snap] {

    /**
      * Creates a snapshot for an instrument. If the resetState flag is set to true, the internal state of the
      * instrument will be reset, if applicable.
      */
    def snapshot(resetState: Boolean): Snap
  }


  /** Internal means of type checking the metric types. This overcomes the fact of all metrics being stored in the same
    * map and allows to communicate a well defined number of options to the Status API.
    */
  final case class Type(name: String, implementation: Class[_])

  object Type {
    val Histogram = Type("histogram", classOf[Metric.Histogram])
    val Counter = Type("counter", classOf[Metric.Counter])
    val Gauge = Type("gauge", classOf[Metric.Gauge])
    val Timer = Type("timer", classOf[Metric.Timer])
    val RangeSampler = Type("rangeSampler", classOf[Metric.RangeSampler])
  }
}

