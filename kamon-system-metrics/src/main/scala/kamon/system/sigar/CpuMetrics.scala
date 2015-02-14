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

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.Sigar

/**
 *  Cpu usage metrics, as reported by Sigar:
 *    - user: Total percentage of system cpu user time.
 *    - system: Total percentage of system cpu kernel time.
 *    - wait: Total percentage of system cpu io wait time.
 *    - idle:  Total percentage of system cpu idle time
 *    - stolen: Total percentage of system cpu involuntary wait time. @see [[https://www.datadoghq.com/2013/08/understanding-aws-stolen-cpu-and-how-it-affects-your-apps/ "Understanding Stolen Cpu"]]
 */
class CpuMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val user = histogram("cpu-user")
  val system = histogram("cpu-system")
  val Wait = histogram("cpu-wait")
  val idle = histogram("cpu-idle")
  val stolen = histogram("cpu-stolen")

  def update(): Unit = {
    val cpuPerc = sigar.getCpuPerc

    user.record((cpuPerc.getUser * 100L).toLong)
    system.record((cpuPerc.getSys * 100L).toLong)
    Wait.record((cpuPerc.getWait * 100L).toLong)
    idle.record((cpuPerc.getIdle * 100L).toLong)
    stolen.record((cpuPerc.getStolen * 100L).toLong)
  }
}

object CpuMetrics extends SigarMetricRecorderCompanion("cpu") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): CpuMetrics =
    new CpuMetrics(sigar, instrumentFactory)
}
