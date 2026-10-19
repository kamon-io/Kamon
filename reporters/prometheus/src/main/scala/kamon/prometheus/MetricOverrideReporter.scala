/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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
package prometheus

import com.typesafe.config.Config
import kamon.metric.{Metric, MetricSnapshot, PeriodSnapshot}
import kamon.module.MetricReporter
import kamon.tag.{Tag, TagSet}

import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsScalaMapConverter}

class MetricOverrideReporter(wrappedReporter: MetricReporter, config: Config = Kamon.config) extends MetricReporter {

  private var metricsMap: Map[String, MetricMapping] =
    getMetricMapping(config)

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val updatedSnapshot = snapshot.copy(
      histograms = snapshot.histograms.map(updateDistribution),
      timers = snapshot.timers.map(updateDistribution),
      rangeSamplers = snapshot.rangeSamplers.map(updateDistribution),
      gauges = snapshot.gauges.map(updateDistribution),
      counters = snapshot.counters.map(updateDistribution)
    )

    wrappedReporter.reportPeriodSnapshot(updatedSnapshot)
  }

  override def stop(): Unit =
    wrappedReporter.stop()

  override def reconfigure(config: Config): Unit = {
    metricsMap = getMetricMapping(config)
    wrappedReporter.reconfigure(config)
  }

  private def remapTags(tags: TagSet, mapping: MetricMapping): TagSet = {
    val remappedTags = TagSet.builder()

    tags.iterator().foreach(tag => {
      if (!mapping.tagsToDelete.contains(tag.key)) {
        remappedTags.add(mapping.tagsToRename.getOrElse(tag.key, tag.key), Tag.unwrapValue(tag).toString)
      }
    })

    remappedTags.build()
  }

  private def updateDistribution[T <: Metric.Settings, U](metric: MetricSnapshot[T, U]): MetricSnapshot[T, U] = {
    metricsMap.get(metric.name).map(mapping => {

      val mappedInstruments = if (mapping.tagsToRename.isEmpty && mapping.tagsToDelete.isEmpty) metric.instruments
      else {
        metric.instruments.map(inst => {
          inst.copy(tags = remapTags(inst.tags, mapping))
        })
      }

      metric.copy(
        name = mapping.newName.getOrElse(metric.name),
        instruments = mappedInstruments
      )

    }).getOrElse(metric)
  }
//
//  private def updateValue(metricValue: MetricValue): MetricValue = {
//    val mappingForValue = metricsMap.get(metricValue.name)
//
//    metricValue.copy(
//      name = mappingForValue.flatMap(_.newName).getOrElse(metricValue.name),
//      tags = mappingForValue.map(
//        mapping => remapTags(metricValue.tags, mapping)
//      ).getOrElse(metricValue.tags)
//    )
//  }

  private def getMetricMapping(config: Config): Map[String, MetricMapping] = {
    val mappingConfig = config.getConfig("kamon.prometheus.metric-overrides")

    mappingConfig.configurations.map { case (name, config) =>
      (
        name,
        MetricMapping(
          if (config.hasPath("name"))
            Some(config.getString("name"))
          else None,
          if (config.hasPath("delete-tags"))
            config.getStringList("delete-tags").asScala.toSet
          else Set.empty,
          if (config.hasPath("rename-tags"))
            config.getObject("rename-tags").unwrapped().asScala.toMap
              .map { case (tagName, value) => (tagName, value.toString) }
          else Map.empty
        )
      )
    }
  }

  private case class MetricMapping(
    newName: Option[String],
    tagsToDelete: Set[String],
    tagsToRename: Map[String, String]
  )
}
