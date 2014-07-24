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

import java.lang.management.ManagementFactory

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.Metrics
import kamon.metrics._
import kamon.system.SystemMetricsCollectorActor.Collect
import org.hyperic.sigar.{ NetInterfaceStat, SigarProxy, Mem }
import scala.concurrent.duration._

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object SystemMetrics extends ExtensionId[SystemMetricsExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = SystemMetrics

  override def createExtension(system: ExtendedActorSystem): SystemMetricsExtension = new SystemMetricsExtension(system)
}

class SystemMetricsExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  import kamon.system.SystemMetricsExtension._

  val log = Logging(system, classOf[SystemMetricsExtension])

  log.info(s"Starting the Kamon(SystemMetrics) extension")

  val systemMetricsExtension = Kamon(Metrics)(system)

  //JVM Metrics
  systemMetricsExtension.register(HeapMetrics(Heap), HeapMetrics.Factory)
  garbageCollectors.map { gc ⇒ systemMetricsExtension.register(GCMetrics(gc.getName), GCMetrics.Factory(gc)) }

  //System Metrics
  system.actorOf(SystemMetricsCollectorActor.props(1 second), "system-metrics-collector")
}

object SystemMetricsExtension {
  val CPU = "cpu"
  val ProcessCPU = "process-cpu"
  val Network = "network"
  val Memory = "memory"
  val Heap = "heap"

  val garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans.asScala.filter(_.isValid)
}

trait SigarExtensionProvider {
  lazy val sigar = SigarHolder.instance()
}

class SystemMetricsCollectorActor(collectInterval: FiniteDuration) extends Actor with SigarExtensionProvider {
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

  def pid = sigar.getPid
  def procCpu = sigar.getProcCpu(pid)
  def cpu = sigar.getCpuPerc
  def mem = sigar.getMem
  def swap = sigar.getSwap

  val interfaces: Set[String] = sigar.getNetInterfaceList.toSet

  def collectMetrics() = {
    cpuRecorder.map {
      cpur ⇒
        cpur.user.record((cpu.getUser * 100L).toLong)
        cpur.system.record((cpu.getSys * 100L).toLong)
        cpur.cpuWait.record((cpu.getWait() * 100L).toLong)
        cpur.idle.record((cpu.getIdle * 100L).toLong)
    }

    processCpuRecorder.map {
      pcpur ⇒
        pcpur.user.record(procCpu.getUser)
        pcpur.system.record(procCpu.getSys)
    }

    memoryRecorder.map {
      mr ⇒
        mr.used.record(mem.getUsed)
        mr.free.record(mem.getFree)
        mr.swapUsed.record(swap.getUsed)
        mr.swapFree.record(swap.getFree)
        mr.buffer.record(collectBuffer(mem))
        mr.cache.record(collectCache(mem))
    }

    networkRecorder.map {
      nr ⇒
        nr.rxBytes.record(collect(sigar, interfaces)(net ⇒ net.getRxBytes))
        nr.txBytes.record(collect(sigar, interfaces)(net ⇒ net.getTxBytes))
        nr.rxErrors.record(collect(sigar, interfaces)(net ⇒ net.getRxErrors))
        nr.txErrors.record(collect(sigar, interfaces)(net ⇒ net.getTxErrors))
    }
  }

  private def collectBuffer(mem: Mem): Long = if (mem.getUsed() != mem.getActualUsed()) mem.getActualUsed() else 0L
  private def collectCache(mem: Mem): Long = if (mem.getFree() != mem.getActualFree()) mem.getActualFree() else 0L

  private def collect(sigar: SigarProxy, interfaces: Set[String])(block: NetInterfaceStat ⇒ Long): Long = {
    interfaces.foldLeft(0L) { (totalBytes, interface) ⇒
      {
        val net = sigar.getNetInterfaceStat(interface)
        totalBytes + block(net)
      }
    }
  }
}

object SystemMetricsCollectorActor {
  case object Collect

  def props(collectInterval: FiniteDuration): Props =
    Props[SystemMetricsCollectorActor](new SystemMetricsCollectorActor(collectInterval))
}