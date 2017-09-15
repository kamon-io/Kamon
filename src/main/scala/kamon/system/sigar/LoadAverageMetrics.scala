/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.system.sigar

import kamon.Kamon
import kamon.metric.Histogram
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  Load Average metrics, as reported by Sigar:
 *    - The system load averages for the past 1, 5, and 15 minutes.
 */
object LoadAverageMetrics extends MetricBuilder("load") with SigarMetricBuilder {
  def build(sigar: Sigar, metricPrefix: String, logger: Logger) = new Metric {
    val aggregations = "1" :: "5" :: "15" :: Nil
    val loadAverageMetrics = LoadAverageMetrics(metricPrefix)

    override def update(): Unit = {
      import SigarSafeRunner._

      val loadAverage = runSafe(sigar.getLoadAverage, Array(0D, 0D, 0D), "load-average", logger)

      aggregations.zipWithIndex.foreach {
        case(aggregation, index) =>
          loadAverageMetrics.forAggregation(aggregation).record(loadAverage(index).toLong)
      }
    }
  }
}

final case class LoadAverageMetrics(metricPrefix:String) {
  val loadAverageMetric = Kamon.histogram(s"$metricPrefix.average")

  def forAggregation(aggregation: String): Histogram = {
    val aggregationTags = Map("aggregation" -> aggregation)
    loadAverageMetric.refine(aggregationTags)
  }
}
