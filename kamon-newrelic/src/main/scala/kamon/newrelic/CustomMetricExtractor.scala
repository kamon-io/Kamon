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

import kamon.metric.{ SimpleMetricsExtensionImpl, EntitySnapshot, Entity }
import kamon.metric.instrument.CollectionContext

object CustomMetricExtractor extends MetricExtractor {

  def extract(settings: AgentSettings, collectionContext: CollectionContext, metrics: Map[Entity, EntitySnapshot]): Map[MetricID, MetricData] = {
    metrics.get(SimpleMetricsExtensionImpl.SimpleMetricsEntity).map { allSimpleMetrics ⇒
      allSimpleMetrics.metrics.map {
        case (key, snapshot) ⇒ Metric(snapshot, key.unitOfMeasurement, s"Custom/${key.name}", None)
      }

    } getOrElse (Map.empty)
  }
}
