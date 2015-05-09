/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import com.typesafe.config.Config
import kamon.metric.instrument._
import kamon.util.GlobPathFilter

import scala.concurrent.duration.FiniteDuration

/**
 *  Configuration settings for the Metrics extension, as read from the `kamon.metric` configuration key.
 */
case class MetricsSettings(
    tickInterval: FiniteDuration,
    defaultCollectionContextBufferSize: Int,
    trackUnmatchedEntities: Boolean,
    entityFilters: Map[String, EntityFilter],
    instrumentFactories: Map[String, InstrumentFactory],
    defaultInstrumentFactory: InstrumentFactory,
    refreshScheduler: RefreshScheduler) {

  private[kamon] def pointScheduler(targetScheduler: RefreshScheduler): Unit = refreshScheduler match {
    case lrs: LazyRefreshScheduler ⇒ lrs.point(targetScheduler)
    case others                    ⇒
  }
}

/**
 *
 */
case class EntityFilter(includes: List[GlobPathFilter], excludes: List[GlobPathFilter]) {
  def accept(name: String): Boolean =
    includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
}

object MetricsSettings {
  import kamon.util.ConfigTools.Syntax

  def apply(config: Config): MetricsSettings = {
    val metricConfig = config.getConfig("kamon.metric")

    val tickInterval = metricConfig.getFiniteDuration("tick-interval")
    val collectBufferSize = metricConfig.getInt("default-collection-context-buffer-size")
    val trackUnmatchedEntities = metricConfig.getBoolean("track-unmatched-entities")
    val entityFilters = loadFilters(metricConfig.getConfig("filters"))
    val defaultInstrumentSettings = DefaultInstrumentSettings.fromConfig(metricConfig.getConfig("default-instrument-settings"))

    val refreshScheduler = new LazyRefreshScheduler
    val instrumentFactories = loadInstrumentFactories(metricConfig.getConfig("instrument-settings"), defaultInstrumentSettings, refreshScheduler)
    val defaultInstrumentFactory = new InstrumentFactory(Map.empty, defaultInstrumentSettings, refreshScheduler)

    MetricsSettings(tickInterval, collectBufferSize, trackUnmatchedEntities, entityFilters, instrumentFactories,
      defaultInstrumentFactory, refreshScheduler)
  }

  /**
   *  Load all the default filters configured under the `kamon.metric.filters` configuration key. All filters are
   *  defined with the entity category as a sub-key of the `kamon.metric.filters` key and two sub-keys to it: includes
   *  and excludes with lists of string glob patterns as values. Example:
   *
   *  {{{
   *
   *    kamon.metrics.filters {
   *      actor {
   *        includes = ["user/test-actor", "user/service/worker-*"]
   *        excludes = ["user/IO-*"]
   *      }
   *    }
   *
   *  }}}
   *
   *  @return a Map from category name to corresponding entity filter.
   */
  def loadFilters(filtersConfig: Config): Map[String, EntityFilter] = {
    import scala.collection.JavaConverters._

    filtersConfig.firstLevelKeys map { category: String ⇒
      val includes = filtersConfig.getStringList(s"$category.includes").asScala.map(inc ⇒ new GlobPathFilter(inc)).toList
      val excludes = filtersConfig.getStringList(s"$category.excludes").asScala.map(exc ⇒ new GlobPathFilter(exc)).toList

      (category, EntityFilter(includes, excludes))
    } toMap
  }

  /**
   *  Load any custom configuration settings defined under the `kamon.metric.instrument-settings` configuration key and
   *  create InstrumentFactories for them.
   *
   * @return a Map from category name to InstrumentFactory.
   */
  def loadInstrumentFactories(instrumentSettings: Config, defaults: DefaultInstrumentSettings, refreshScheduler: RefreshScheduler): Map[String, InstrumentFactory] = {
    instrumentSettings.firstLevelKeys.map { category ⇒
      val categoryConfig = instrumentSettings.getConfig(category)
      val customSettings = categoryConfig.firstLevelKeys.map { instrumentName ⇒
        (instrumentName, InstrumentCustomSettings.fromConfig(categoryConfig.getConfig(instrumentName)))
      } toMap

      (category, new InstrumentFactory(customSettings, defaults, refreshScheduler))
    } toMap
  }
}
