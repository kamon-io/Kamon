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

import kamon.tag.TagSet

/**
  * Utility class for handling groups of instruments that should be created and removed together. This becomes specially
  * handy when using several instruments to track different aspects of the same component. For example, when tracking
  * metrics on a thread pool you will want to request several instruments to track different aspects: the pool size, the
  * number of submitted tasks, the queue size and so on, and all of those instruments should share common tags that are
  * specific to the instrumented thread pool; additionally, once said thread pool is shutdown, all of those instruments
  * should be removed together. This class makes it simpler to keep track of all of those related instruments and remove
  * them together when necessary.
  */
abstract class InstrumentGroup(val commonTags: TagSet) {
  private var _groupInstruments = List.empty[Instrument[_, _]]

  /**
    * Registers and returns an instrument of the provided metric with the common tags.
    */
  def register[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](metric: Metric[Inst, Sett]): Inst =
    registerInstrument(metric, commonTags)

  /**
    * Registers and returns an instrument of the provided metric with the common tags and the additionally provided
    * key/value pair.
    */
  def register[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    key: String,
    value: String
  ): Inst =
    registerInstrument(metric, commonTags.withTag(key, value))

  /**
    * Registers and returns an instrument of the provided metric with the common tags and the additionally provided
    * key/value pair.
    */
  def register[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    key: String,
    value: Long
  ): Inst =
    registerInstrument(metric, commonTags.withTag(key, value))

  /**
    * Registers and returns an instrument of the provided metric with the common tags and the additionally provided
    * key/value pair.
    */
  def register[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    key: String,
    value: Boolean
  ): Inst =
    registerInstrument(metric, commonTags.withTag(key, value))

  /**
    * Registers and returns an instrument of the provided metric with the common tags and the additionally provided tags.
    */
  def register[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    extraTags: TagSet
  ): Inst =
    registerInstrument(metric, commonTags.withTags(extraTags))

  private def registerInstrument[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings](
    metric: Metric[Inst, Sett],
    tags: TagSet
  ): Inst = synchronized {
    val instrument = metric.withTags(tags)
    _groupInstruments = instrument :: _groupInstruments
    instrument
  }

  /**
    * Removes all instruments that were registered by this group.
    */
  def remove(): Unit = synchronized {
    _groupInstruments foreach (_.remove())
  }
}
