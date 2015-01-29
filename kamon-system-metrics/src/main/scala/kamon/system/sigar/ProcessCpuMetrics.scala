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
import org.hyperic.sigar.{ ProcCpu, Sigar }

class ProcessCpuMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val processUserCpu = histogram("process-user-cpu")
  val processSystemCpu = histogram("process-system-cpu")
  val processTotalCpu = histogram("process-cpu")

  var lastProcCpu: Option[ProcCpu] = None

  def update(): Unit = {
    val pid = sigar.getPid
    val procCpu = sigar.getProcCpu(pid)

    lastProcCpu.map { last ⇒
      val timeDiff = procCpu.getLastTime - last.getLastTime
      if (timeDiff > 0) {
        val userPercent = (((procCpu.getUser - last.getUser) / timeDiff.toDouble) * 100).toLong
        val systemPercent = (((procCpu.getSys - last.getSys) / timeDiff.toDouble) * 100).toLong

        processUserCpu.record(userPercent)
        processSystemCpu.record(systemPercent)
        processTotalCpu.record(userPercent + systemPercent)
      }
    }

    lastProcCpu = Some(procCpu)

  }
}

object ProcessCpuMetrics extends SigarMetricRecorderCompanion("process-cpu") {

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): ProcessCpuMetrics =
    new ProcessCpuMetrics(sigar, instrumentFactory)
}
