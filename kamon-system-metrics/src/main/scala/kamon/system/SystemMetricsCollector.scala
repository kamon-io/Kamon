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

import java.io.{ File, IOException }

import akka.actor.{ Actor, ActorLogging, Props }
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metrics.CPUMetrics.CPUMetricRecorder
import kamon.metrics.ContextSwitchesMetrics.ContextSwitchesMetricsRecorder
import kamon.metrics.MemoryMetrics.MemoryMetricRecorder
import kamon.metrics.NetworkMetrics.NetworkMetricRecorder
import kamon.metrics.ProcessCPUMetrics.ProcessCPUMetricsRecorder
import kamon.metrics._
import kamon.sigar.SigarProvisioner
import org.hyperic.sigar.{ Sigar, Mem, NetInterfaceStat, SigarProxy }

import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import scala.util.control.NonFatal

class SystemMetricsCollector(collectInterval: FiniteDuration) extends Actor with ActorLogging with SystemMetricsBanner {
  import kamon.system.SystemMetricsCollector._
  import kamon.system.SystemMetricsExtension._

  lazy val sigar = createSigarInstance
  def pid = sigar.getPid

  val interfaces: Set[String] = sigar.getNetInterfaceList.toSet

  val systemMetricsExtension = Kamon(Metrics)(context.system)
  val collectSchedule = context.system.scheduler.schedule(collectInterval, collectInterval, self, Collect)(context.dispatcher)

  val cpuRecorder = systemMetricsExtension.register(CPUMetrics(CPU), CPUMetrics.Factory)
  val processCpuRecorder = systemMetricsExtension.register(ProcessCPUMetrics(ProcessCPU), ProcessCPUMetrics.Factory)
  val memoryRecorder = systemMetricsExtension.register(MemoryMetrics(Memory), MemoryMetrics.Factory)
  val networkRecorder = systemMetricsExtension.register(NetworkMetrics(Network), NetworkMetrics.Factory)
  val contextSwitchesRecorder = systemMetricsExtension.register(ContextSwitchesMetrics(ContextSwitches), ContextSwitchesMetrics.Factory)

  def receive: Receive = {
    case Collect ⇒ collectMetrics()
  }

  override def postStop() = collectSchedule.cancel()

  def collectMetrics() = {
    cpuRecorder.map(recordCpu)
    processCpuRecorder.map(recordProcessCpu)
    memoryRecorder.map(recordMemory)
    networkRecorder.map(recordNetwork)

    if (OsUtils.isLinux)
      contextSwitchesRecorder.map(recordContextSwitches)
  }

  private def recordCpu(cpur: CPUMetricRecorder) = {
    val cpuPerc = sigar.getCpuPerc
    cpur.user.record(toLong(cpuPerc.getUser))
    cpur.system.record(toLong(cpuPerc.getSys))
    cpur.cpuWait.record(toLong(cpuPerc.getWait))
    cpur.idle.record(toLong(cpuPerc.getIdle))
    cpur.stolen.record(toLong(cpuPerc.getStolen))
  }

  private def recordProcessCpu(pcpur: ProcessCPUMetricsRecorder) = {
    val procCpu = sigar.getProcCpu(pid)
    val procTime = sigar.getProcTime(pid)

    pcpur.cpuPercent.record(toLong(procCpu.getPercent))
    pcpur.totalProcessTime.record(procTime.getTotal) // gives an idea of what is really measured and then interpreted as %
  }

  private def recordMemory(mr: MemoryMetricRecorder) = {
    val mem = sigar.getMem
    val swap = sigar.getSwap

    mr.used.record(toMB(mem.getUsed))
    mr.free.record(toMB(mem.getFree))
    mr.swapUsed.record(toMB(swap.getUsed))
    mr.swapFree.record(toMB(swap.getFree))
    mr.buffer.record(toMB(collectBuffer(mem)))
    mr.cache.record(toMB(collectCache(mem)))

    def collectBuffer(mem: Mem): Long = if (mem.getUsed != mem.getActualUsed) mem.getActualUsed else 0L
    def collectCache(mem: Mem): Long = if (mem.getFree != mem.getActualFree) mem.getActualFree else 0L
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

  private def recordContextSwitches(rcs: ContextSwitchesMetricsRecorder) = {
    def contextSwitchesByProcess(pid: Long): (Long, Long) = {
      val filename = s"/proc/$pid/status"
      var voluntaryContextSwitches = 0L
      var nonVoluntaryContextSwitches = 0L

      try {
        for (line ← Source.fromFile(filename).getLines()) {
          if (line.startsWith("voluntary_ctxt_switches")) {
            voluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
          if (line.startsWith("nonvoluntary_ctxt_switches")) {
            nonVoluntaryContextSwitches = line.substring(line.indexOf(":") + 1).trim.toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      (voluntaryContextSwitches, nonVoluntaryContextSwitches)
    }

    def contextSwitches: Long = {
      val filename = "/proc/stat"
      var contextSwitches = 0L

      try {
        for (line ← Source.fromFile(filename).getLines()) {
          if (line.startsWith("rcs")) {
            contextSwitches = line.substring(line.indexOf(" ") + 1).toLong
          }
        }
      } catch {
        case ex: IOException ⇒ log.error("Error trying to read [{}]", filename)
      }
      contextSwitches
    }

    val (perProcessVoluntary, perProcessNonVoluntary) = contextSwitchesByProcess(pid)
    rcs.perProcessVoluntary.record(perProcessVoluntary)
    rcs.perProcessNonVoluntary.record(perProcessNonVoluntary)
    rcs.global.record(contextSwitches)
  }

  def verifiedSigarInstance: SigarProxy = {
    val sigar = new Sigar()
    printBanner(sigar)
    sigar
  }

  def provisionSigarLibrary: Unit = {
    val folder = context.system.settings.config.getString("kamon.sigar.folder")
    SigarProvisioner.provision(new File(folder))
  }

  def createSigarInstance: SigarProxy = {

    // 1) Assume that library is already provisioned.
    try {
      return verifiedSigarInstance
    } catch {
      // Not using [[Try]] - any error is non-fatal in this case.
      case e: Throwable ⇒ log.info(s"Sigar is not yet provisioned: ${e}")
    }

    // 2) Attempt to provision library via sigar-loader.
    try {
      provisionSigarLibrary
      return verifiedSigarInstance
    } catch {
      // Not using [[Try]] - any error is non-fatal in this case.
      case e: Throwable ⇒ throw new UnexpectedSigarException(s"Failed to load Sigar: ${e}")
    }
  }
}

object SystemMetricsCollector {
  case object Collect

  object OsUtils {
    def isLinux: Boolean = System.getProperty("os.name").indexOf("Linux") != -1
  }

  def props(collectInterval: FiniteDuration): Props = Props[SystemMetricsCollector](new SystemMetricsCollector(collectInterval))
}