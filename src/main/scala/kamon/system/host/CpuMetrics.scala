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
import kamon.metric.Histogram
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

/**
 *  Cpu usage metrics, as reported by Sigar:
 *    - user: Total percentage of system cpu user time.
 *    - system: Total percentage of system cpu kernel time.
 *    - wait: Total percentage of system cpu io wait time.
 *    - idle:  Total percentage of system cpu idle time
 *    - stolen: Total percentage of system cpu involuntary wait time. @see [[https://www.datadoghq.com/2013/08/understanding-aws-stolen-cpu-and-how-it-affects-your-apps/ "Understanding Stolen Cpu"]]
 *    - combined:  Total percentage of user + system + nice + wait CPU usage.
  */
object CpuMetrics extends MetricBuilder("host.cpu") with SigarMetricBuilder {

  def build(sigar: Sigar, metricName: String, logger: Logger) = new Metric {
    val cpuMetric = new CpuMetric(metricName)

    def update(): Unit = {
      import SigarSafeRunner._

      def cpuPerc = {
        val cpuPerc = sigar.getCpuPerc
        ((cpuPerc.getUser * 100L).toLong,
          (cpuPerc.getSys * 100L).toLong,
          (cpuPerc.getWait * 100L).toLong,
          (cpuPerc.getIdle * 100L).toLong,
          (cpuPerc.getStolen * 100L).toLong,
          (cpuPerc.getCombined * 100L).toLong)
      }

      val (cpuUser, cpuSys, cpuWait, cpuIdle, cpuStolen, cpuCombined) = runSafe(cpuPerc, (0L, 0L, 0L, 0L, 0L, 0L), "cpu", logger)

      cpuMetric.forMode("user").record(cpuUser)
      cpuMetric.forMode("system").record(cpuSys)
      cpuMetric.forMode("wait").record(cpuWait)
      cpuMetric.forMode("idle").record(cpuIdle)
      cpuMetric.forMode("stolen").record(cpuStolen)
      cpuMetric.forMode("combined").record(cpuCombined)
    }
  }

  class CpuMetric(metricName: String) {
    val cpuMetric = Kamon.histogram(metricName)

    def forMode(mode: String): Histogram =
      cpuMetric.refine(Map("component" -> "system-metrics", "mode" -> mode))
  }
}



