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
import java.util.function.Consumer

import kamon.tag.TagSet

/**
  * Base user-facing API for all metric instruments in Kamon.
  */
trait Instrument[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings] extends Tagging[Inst] {

  /**
    * Returns the metric to which this instrument belongs.
    */
  def metric: Metric[Inst, Sett]

  /**
    * Returns the tags used to create this instrument.
    */
  def tags: TagSet

  /**
    * Removes this instrument from the metric.
    */
  def remove(): Boolean =
    metric.remove(tags)

  override def withTag(key: String, value: String): Inst =
    metric.withTags(tags.withTag(key, value))

  override def withTag(key: String, value: Boolean): Inst =
    metric.withTags(tags.withTag(key, value))

  override def withTag(key: String, value: Long): Inst =
    metric.withTags(tags.withTag(key, value))

  override def withTags(tags: TagSet): Inst =
    metric.withTags(this.tags.withTags(tags))

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
    * Snapshot of an instrument's state at a given point. Snapshots are expected to have either Long, Double or
    * Distribution values, depending on the instrument type.
    */
  case class Snapshot[T](
    tags: TagSet,
    value: T
  )

  /**
    * Exposes the required API to create instrument snapshots snapshots. This API is not meant to be exposed to users.
    */
  private[kamon] trait Snapshotting[Snap] {

    /**
      * Creates a snapshot for an instrument. If the resetState flag is set to true, the internal state of the
      * instrument will be reset, if applicable.
      */
    def snapshot(resetState: Boolean): Snap
  }

  /**
    * Internal means of type checking the metric types. This overcomes the fact of all metrics being stored in the same
    * map and allows to communicate a well defined number of options to the Status API.
    */
  private[kamon] final case class Type(name: String, implementation: Class[_])

  object Type {
    val Histogram = Type("histogram", classOf[Metric.Histogram])
    val Counter = Type("counter", classOf[Metric.Counter])
    val Gauge = Type("gauge", classOf[Metric.Gauge])
    val Timer = Type("timer", classOf[Metric.Timer])
    val RangeSampler = Type("rangeSampler", classOf[Metric.RangeSampler])
  }
}
