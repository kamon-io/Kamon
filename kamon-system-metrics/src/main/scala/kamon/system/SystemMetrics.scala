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
import kamon.metric.Metrics
import kamon.metrics.CpuMetrics.CpuMetricRecorder
import kamon.metrics.MemoryMetrics.MemoryMetricRecorder
import kamon.metrics.NetworkMetrics.NetworkMetricRecorder
import kamon.metrics.ProcessCpuMetrics.ProcessCpuMetricsRecorder
import kamon.metrics._
import kamon.system.native.SigarExtensionProvider
import org.hyperic.sigar._

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics
  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(private val system: ExtendedActorSystem) extends Kamon.Extension with SigarExtensionProvider {
  val log = Logging(system, classOf[SystemMetricsExtension])
  val pid = sigar.getPid

  log.info(s"Starting the Kamon(SystemMetrics) extension with pid: $pid")

  val systemMetricsConfig = system.settings.config.getConfig("kamon.system-metrics")
  val systemMetricsExtension = Kamon(Metrics)(system)

  val cpuMetricsRecorder = systemMetricsExtension.register(CpuMetrics("cpu"), CpuMetrics.Factory)
  val processCpuMetricsRecorder = systemMetricsExtension.register(ProcessCpuMetrics("process-cpu"), ProcessCpuMetrics.Factory)
  val networkMetricsRecorder = systemMetricsExtension.register(NetworkMetrics("network"), NetworkMetrics.Factory)
  val memoryMetricsRecorder = systemMetricsExtension.register(MemoryMetrics("memory"), MemoryMetrics.Factory)


  systemMetricsExtension.scheduleGaugeRecorder {
    cpuMetricsRecorder.map(recordCpuMetrics(sigar.getCpu))
  }

  systemMetricsExtension.scheduleGaugeRecorder {
    processCpuMetricsRecorder.map(recordProcessCpuMetrics(sigar.getProcCpu(pid)))
  }

  systemMetricsExtension.scheduleGaugeRecorder {
    networkMetricsRecorder.map(recordNetworkMetrics(sigar))
  }

  systemMetricsExtension.scheduleGaugeRecorder {
    memoryMetricsRecorder.map(recordMemoryMetrics(sigar.getMem)(sigar.getSwap))
  }

  private[system] def recordCpuMetrics(cpu: Cpu)(recorder: CpuMetricRecorder) = {
    val CpuMetricsMeasurement(user, system, cpuWait, idle) = CpuMetricsCollector.collect(cpu)

    recorder.user.record(user)
    recorder.system.record(system)
    recorder.cpuWait.record(cpuWait)
    recorder.idle.record(idle)
  }

  private[system] def recordProcessCpuMetrics(cpu: ProcCpu)(recorder: ProcessCpuMetricsRecorder) = {
    val ProcessCpuMetricsMeasurement(user, system) = ProcessCpuMetricsCollector.collect(cpu)

    recorder.user.record(user)
    recorder.system.record(system)
  }

  private[system] def recordNetworkMetrics(sigar: SigarProxy)(recorder: NetworkMetricRecorder) = {
    val NetworkMetricsMeasurement(rxBytes, txBytes, rxErrors, txErrors) = NetWorkMetricsCollector.collect(sigar)

    recorder.rxBytes.record(rxBytes)
    recorder.txBytes.record(txBytes)
    recorder.rxErrors.record(rxErrors)
    recorder.txErrors.record(txErrors)
  }

  private[system] def recordMemoryMetrics(mem: Mem)(swap: Swap)(recorder: MemoryMetricRecorder) = {
    val MemoryMetricsMeasurement(used, free, buffer, cache, swapUsed, swapFree) = MemoryMetricsCollector.collect(mem, swap)

    recorder.used.record(used)
    recorder.free.record(free)
    recorder.buffer.record(buffer)
    recorder.cache.record(cache)
    recorder.swapUsed.record(swapUsed)
    recorder.swapFree.record(swapFree)
  }
}

