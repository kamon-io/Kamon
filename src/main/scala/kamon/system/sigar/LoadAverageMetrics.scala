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

package kamon.system.sigar

import kamon.Kamon
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  Load Average metrics, as reported by Sigar:
 *    - The system load averages for the past 1, 5, and 15 minutes.
 */
class LoadAverageMetrics(sigar: Sigar, metricPrefix: String, logger: Logger) extends SigarMetric {
  import SigarSafeRunner._

  val aggregationKey = "aggregation"

  val baseHistogram   = Kamon.histogram(metricPrefix+"average")
  val oneMinute       = baseHistogram.refine(Map(aggregationKey -> "1"))
  val fiveMinutes     = baseHistogram.refine(Map(aggregationKey -> "5"))
  val fifteenMinutes  = baseHistogram.refine(Map(aggregationKey -> "15"))

  def update(): Unit = {
    val loadAverage = runSafe(sigar.getLoadAverage, Array(0D, 0D, 0D), "load-average", logger)

    oneMinute.record(loadAverage(0).toLong)
    fiveMinutes.record(loadAverage(1).toLong)
    fifteenMinutes.record(loadAverage(2).toLong)
  }
}

object LoadAverageMetrics extends SigarMetricRecorderCompanion("load") {

  def apply(sigar: Sigar, metricPrefix: String, logger: Logger): LoadAverageMetrics =
    new LoadAverageMetrics(sigar, metricPrefix, logger)
}
