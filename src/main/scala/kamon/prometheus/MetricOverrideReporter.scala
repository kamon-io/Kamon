/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon
package prometheus

import scala.collection.JavaConverters.{asScalaBuffer, mapAsScalaMap}

import com.typesafe.config.Config
import kamon.metric.{MetricDistribution, MetricValue, PeriodSnapshot}

class MetricOverrideReporter(wrappedReporter: MetricReporter, config: Config = Kamon.config) extends MetricReporter {

  private var metricsMap: Map[String, MetricMapping] =
    getMetricMapping(config)

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val updatedMetrics = snapshot.metrics.copy(
      histograms = snapshot.metrics.histograms.map(updateDistribution),
      rangeSamplers = snapshot.metrics.rangeSamplers.map(updateDistribution),
      gauges = snapshot.metrics.gauges.map(updateValue),
      counters = snapshot.metrics.counters.map(updateValue))

    wrappedReporter.reportPeriodSnapshot(snapshot.copy(metrics = updatedMetrics))
  }

  override def start(): Unit =
    wrappedReporter.start()

  override def stop(): Unit =
    wrappedReporter.stop()

  override def reconfigure(config: Config): Unit = {
    metricsMap = getMetricMapping(config)
    wrappedReporter.reconfigure(config)
  }

  private def remapTags(tags: kamon.Tags, mapping: MetricMapping): kamon.Tags = {
    tags.collect {
      case (name, value) if !mapping.tagsToDelete.contains(name) =>
        (mapping.tagsToRename.getOrElse(name, name), value)
    }
  }

  private def updateDistribution(metricDistribution: MetricDistribution): MetricDistribution = {
    val mappingForDistribution = metricsMap.get(metricDistribution.name)

    metricDistribution.copy(
      name = mappingForDistribution.flatMap(_.newName).getOrElse(metricDistribution.name),
      tags = mappingForDistribution.map(
        mapping => remapTags(metricDistribution.tags, mapping)
      ).getOrElse(metricDistribution.tags)
    )
  }

  private def updateValue(metricValue: MetricValue): MetricValue = {
    val mappingForValue = metricsMap.get(metricValue.name)

    metricValue.copy(
      name = mappingForValue.flatMap(_.newName).getOrElse(metricValue.name),
      tags = mappingForValue.map(
        mapping => remapTags(metricValue.tags, mapping)
      ).getOrElse(metricValue.tags)
    )
  }

  private def getMetricMapping(config: Config): Map[String, MetricMapping] = {
    val mappingConfig = config.getConfig("kamon.prometheus.metric-overrides")

    mappingConfig.configurations.map { case (name, config) =>
      (name, MetricMapping(
        if (config.hasPath("name"))
          Some(config.getString("name")) else None,
        if (config.hasPath("delete-tags"))
          asScalaBuffer(config.getStringList("delete-tags")).toSet else Set.empty,
        if (config.hasPath("rename-tags"))
          mapAsScalaMap(config.getObject("rename-tags").unwrapped()).toMap
            .map { case (tagName, value) => (tagName, value.toString) } else Map.empty
      ))
    }
  }

  private case class MetricMapping(newName: Option[String],
                                   tagsToDelete: Set[String],
                                   tagsToRename: Map[String, String])
}
