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

package kamon.system.host

import kamon.Kamon
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

object ULimitMetrics extends MetricBuilder("host.ulimit") with SigarMetricBuilder {
  def build(sigar: Sigar, metricName: String, logger: Logger) = new Metric {
    val pid = sigar.getPid
    val ulimitMetric = Kamon.histogram(metricName)
    val openFilesMetric = ulimitMetric.refine(Map("component" -> "system-metrics", "limit" -> "open-files"))

    override def update(): Unit = {
      import SigarSafeRunner._

      openFilesMetric.record(runSafe(sigar.getProcFd(pid).getTotal, 0L, "open-files", logger))
    }
  }
}
