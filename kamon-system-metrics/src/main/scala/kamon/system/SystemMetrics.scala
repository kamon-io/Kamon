/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package kamon.system

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metrics.CpuMetrics.CpuMetricRecorder
import kamon.metrics._
import kamon.system.native.SigarExtensionProvider
import org.hyperic.sigar.Cpu

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics

  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(private val system: ExtendedActorSystem) extends Kamon.Extension with SigarExtensionProvider {
  val log = Logging(system, classOf[SystemMetricsExtension])

  log.info("Starting the Kamon(SystemMetrics) extension")

  val systemMetricsConfig = system.settings.config.getConfig("kamon.system-metrics")
  val globalCpuUsage = systemMetricsConfig.getBoolean("cpu.global")

  val systemMetricsExtension = Kamon(Metrics)(system)

  if (globalCpuUsage) {
    val globalCpuMetricsRecorder = systemMetricsExtension.register(CpuMetrics("global-cpu-usage"), CpuMetrics.Factory)
    systemMetricsExtension.scheduleGaugeRecorder {
      globalCpuMetricsRecorder.map(recordCpuMetrics(sigar.getCpu))
    }
  } else {
    sigar.getCpuList.zipWithIndex.foreach {
      case (cpu, id) =>
        val cpuMetricsRecorder = systemMetricsExtension.register(CpuMetrics(s"cpu:$id"), CpuMetrics.Factory)

        if (cpuMetricsRecorder.isDefined) {
          systemMetricsExtension.scheduleGaugeRecorder {
            cpuMetricsRecorder.map(recordCpuMetrics(cpu))
          }
        }
    }
  }

  def recordCpuMetrics(cpu: Cpu)(recorder: CpuMetricRecorder) = {
    val CpuMetricsMeasurement(user, system, cpuWait, idle) = CpuMetricsCollector.collect(cpu)

    recorder.user.record(user)
    recorder.system.record(system)
    recorder.cpuWait.record(cpuWait)
    recorder.idle.record(idle)
  }

  sigar.getNetInterfaceList.toSet.foreach {
    interface =>
      val networkMetricsRecorder = systemMetricsExtension.register(NetworkMetrics(interface), NetworkMetrics.Factory)

      if (networkMetricsRecorder.isDefined) {
        systemMetricsExtension.scheduleGaugeRecorder {
          networkMetricsRecorder.map {
            nr =>
              val NetworkMetricsMeasurement(rxBytes, txBytes, rxErrors, txErrors) = NetWorkMetricsCollector.collect()

              nr.rxBytes.record(rxBytes)
              nr.txBytes.record(txBytes)
              nr.rxErrors.record(rxErrors)
              nr.txErrors.record(txErrors)
          }
        }
      }

  }

  val memoryMetricsRecorder = systemMetricsExtension.register(MemoryMetrics("system-memory"), MemoryMetrics.Factory)

  systemMetricsExtension.scheduleGaugeRecorder {

    memoryMetricsRecorder.map {
      mr =>
        val MemoryMetricsMeasurement(used, free, buffer, cache, swapUsed, swapFree) = MemoryMetricsCollector.collect(sigar.getMem, sigar.getSwap)

        mr.used.record(used)
        mr.free.record(free)
        mr.buffer.record(buffer)
        mr.cache.record(cache)
        mr.swapUsed.record(swapUsed)
        mr.swapFree.record(swapFree)
    }
  }

  val pid = sigar.getPid
  val currentProcCpu = sigar.getProcCpu(pid)

  val currentProcCpuMetricsRecorder = systemMetricsExtension.register(CpuMetrics("proc-cpu"), CpuMetrics.Factory)

  systemMetricsExtension.scheduleGaugeRecorder {
    currentProcCpuMetricsRecorder.map {
      pcpu =>
        pcpu.system.record(currentProcCpu.getSys)
        pcpu.user.record(currentProcCpu.getUser)
    }
  }

}

