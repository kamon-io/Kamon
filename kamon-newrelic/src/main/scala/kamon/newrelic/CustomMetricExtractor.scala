/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import kamon.metric.{ MetricKey, EntitySnapshot, Entity }
import kamon.metric.instrument.CollectionContext

object CustomMetricExtractor extends MetricExtractor {

  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[Entity, EntitySnapshot]): Map[MetricID, MetricData] = {
    val (simple, complex) = metrics.partition(simpleMetrics)
    simple.map(toNewRelicMetric(simpleName)) ++ complex.map(toNewRelicMetric(complexName))
  }

  def simpleName(entity: Entity, metricKey: MetricKey) = s"Custom/${entity.category}/${normalize(entity.name)}"

  def complexName(entity: Entity, metricKey: MetricKey) = s"${simpleName(entity, metricKey)}/${metricKey.name}"

  def normalize(name: String) = name.replace('/', '\\').replaceAll("""[\]\[\|\*]""", "_")

  def simpleMetrics(kv: (Entity, EntitySnapshot)): Boolean =
    kamon.metric.SingleInstrumentEntityRecorder.AllCategories.contains(kv._1.category)

  def toNewRelicMetric(name: (Entity, MetricKey) ⇒ String)(kv: (Entity, EntitySnapshot)): (MetricID, MetricData) = {
    val (entity, entitySnapshot) = kv
    val (metricKey, instrumentSnapshot) = entitySnapshot.metrics.head
    val nameStr = name(entity, metricKey)

    Metric(instrumentSnapshot, metricKey.unitOfMeasurement, nameStr, None)
  }

}
