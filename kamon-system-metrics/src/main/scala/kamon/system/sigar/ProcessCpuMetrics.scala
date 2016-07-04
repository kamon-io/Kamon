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

import akka.event.LoggingAdapter
import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.InstrumentFactory
import org.hyperic.sigar.{ ProcCpu, Sigar }

import scala.util.Try

/**
 *  Process Cpu usage metrics, as reported by Sigar:
 *    - user:  Process cpu user time.
 *    - total: Process cpu time (sum of User and Sys).
 *    - system: Process cpu kernel time.
 */
class ProcessCpuMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {

  val processUserCpu = histogram("process-user-cpu")
  val processSystemCpu = histogram("process-system-cpu")
  val processTotalCpu = histogram("process-cpu")

  val pid = sigar.getPid
  val totalCores = sigar.getCpuInfoList.headOption.map(_.getTotalCores.toLong).getOrElse(1L)

  var lastProcCpu: ProcCpu = sigar.getProcCpu(pid)
  var currentLoad: Long = 0

  /**
   * While CPU usage time updates not very often, We have introduced a simple heuristic, that supposes that the load is the same as previous,
   * while CPU usage time doesn't update. But supposing that it could be zero load for a process for some time,
   * We used an arbitrary duration of 2000 milliseconds, after which the same CPU usage time value become legal, and it is supposed that the load is really zero.
   *
   * @see [[http://stackoverflow.com/questions/19323364/using-sigar-api-to-get-jvm-cpu-usage "StackOverflow: Using Sigar API to get JVM Cpu usage"]]
   */
  def update(): Unit = {

    def percentUsage(delta: Long, timeDiff: Long): Long = Try(100 * delta / timeDiff / totalCores).getOrElse(0L)

    def positiveSubtraction(left: Long, right: Long): Long = {
      val result = left - right
      if (result < 0L) 0L else result
    }

    val currentProcCpu = sigar.getProcCpu(pid)
    val totalDiff = positiveSubtraction(currentProcCpu.getTotal, lastProcCpu.getTotal)
    val userDiff = positiveSubtraction(currentProcCpu.getUser, lastProcCpu.getUser)
    val systemDiff = positiveSubtraction(currentProcCpu.getSys, lastProcCpu.getSys)
    val timeDiff = currentProcCpu.getLastTime - lastProcCpu.getLastTime

    if (totalDiff == 0L) {
      if (timeDiff > 2000L) currentLoad = 0L
      if (currentLoad == 0L) lastProcCpu = currentProcCpu
    } else {
      val totalPercent = percentUsage(totalDiff, timeDiff)
      val userPercent = percentUsage(userDiff, timeDiff)
      val systemPercent = percentUsage(systemDiff, timeDiff)

      processUserCpu.record(userPercent)
      processSystemCpu.record(systemPercent)
      processTotalCpu.record(userPercent + systemPercent)

      currentLoad = totalPercent
      lastProcCpu = currentProcCpu
    }
  }
}

object ProcessCpuMetrics extends SigarMetricRecorderCompanion("process-cpu") {
  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter): ProcessCpuMetrics =
    new ProcessCpuMetrics(sigar, instrumentFactory, logger)
}
