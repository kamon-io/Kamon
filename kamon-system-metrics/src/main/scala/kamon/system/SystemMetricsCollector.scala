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

import akka.actor.{ Actor, Props }
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metrics.CPUMetrics.CPUMetricRecorder
import kamon.metrics.MemoryMetrics.MemoryMetricRecorder
import kamon.metrics.NetworkMetrics.NetworkMetricRecorder
import kamon.metrics.ProcessCPUMetrics.ProcessCPUMetricsRecorder
import kamon.metrics.{ CPUMetrics, MemoryMetrics, NetworkMetrics, ProcessCPUMetrics }
import kamon.system.sigar.SigarHolder
import org.hyperic.sigar.{ Mem, NetInterfaceStat, SigarProxy }

import scala.concurrent.duration.FiniteDuration

class SystemMetricsCollector(collectInterval: FiniteDuration) extends Actor with SigarExtensionProvider {
  import kamon.system.SystemMetricsCollector._
  import kamon.system.SystemMetricsExtension._

  val collectSchedule = context.system.scheduler.schedule(collectInterval, collectInterval, self, Collect)(context.dispatcher)

  val systemMetricsExtension = Kamon(Metrics)(context.system)

  val cpuRecorder = systemMetricsExtension.register(CPUMetrics(CPU), CPUMetrics.Factory)
  val processCpuRecorder = systemMetricsExtension.register(ProcessCPUMetrics(ProcessCPU), ProcessCPUMetrics.Factory)
  val memoryRecorder = systemMetricsExtension.register(MemoryMetrics(Memory), MemoryMetrics.Factory)
  val networkRecorder = systemMetricsExtension.register(NetworkMetrics(Network), NetworkMetrics.Factory)

  def receive: Receive = {
    case Collect  ⇒ collectMetrics()
    case anything ⇒
  }

  override def postStop() = collectSchedule.cancel()

  def collectMetrics() = {
    cpuRecorder.map(recordCpu)
    processCpuRecorder.map(recordProcessCpu)
    memoryRecorder.map(recordMemory)
    networkRecorder.map(recordNetwork)
  }

  private def recordCpu(cpur: CPUMetricRecorder) = {
    cpur.user.record(toLong(cpu.getUser))
    cpur.system.record(toLong(cpu.getSys))
    cpur.cpuWait.record(toLong(cpu.getWait()))
    cpur.idle.record(toLong(cpu.getIdle))
  }

  private def recordProcessCpu(pcpur: ProcessCPUMetricsRecorder) = {
    pcpur.user.record(procCpu.getUser)
    pcpur.system.record(procCpu.getSys)
  }

  private def recordMemory(mr: MemoryMetricRecorder) = {
    mr.used.record(toMB(mem.getUsed))
    mr.free.record(toMB(mem.getFree))
    mr.swapUsed.record(toMB(swap.getUsed))
    mr.swapFree.record(toMB(swap.getFree))
    mr.buffer.record(toMB(collectBuffer(mem)))
    mr.cache.record(toMB(collectCache(mem)))

    def collectBuffer(mem: Mem): Long = if (mem.getUsed() != mem.getActualUsed()) mem.getActualUsed() else 0L
    def collectCache(mem: Mem): Long = if (mem.getFree() != mem.getActualFree()) mem.getActualFree() else 0L
  }

  private def recordNetwork(nr: NetworkMetricRecorder) = {
    nr.rxBytes.record(collect(sigar, interfaces)(net ⇒ toKB(net.getRxBytes)))
    nr.txBytes.record(collect(sigar, interfaces)(net ⇒ toKB(net.getTxBytes)))
    nr.rxErrors.record(collect(sigar, interfaces)(net ⇒ net.getRxErrors))
    nr.txErrors.record(collect(sigar, interfaces)(net ⇒ net.getTxErrors))

    def collect(sigar: SigarProxy, interfaces: Set[String])(block: NetInterfaceStat ⇒ Long): Long = {
      interfaces.foldLeft(0L) { (totalBytes, interface) ⇒
        {
          val net = sigar.getNetInterfaceStat(interface)
          totalBytes + block(net)
        }
      }
    }
  }
}

object SystemMetricsCollector {
  case object Collect

  def props(collectInterval: FiniteDuration): Props = Props[SystemMetricsCollector](new SystemMetricsCollector(collectInterval))
}

trait SigarExtensionProvider {
  lazy val sigar = SigarHolder.instance()

  def pid = sigar.getPid
  def procCpu = sigar.getProcCpu(pid)
  def cpu = sigar.getCpuPerc
  def mem = sigar.getMem
  def swap = sigar.getSwap

  val interfaces: Set[String] = sigar.getNetInterfaceList.toSet
}
