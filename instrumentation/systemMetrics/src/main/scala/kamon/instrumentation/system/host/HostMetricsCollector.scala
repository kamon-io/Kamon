/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package instrumentation
package system
package host

import com.typesafe.config.Config
import kamon.instrumentation.system.host.HostMetrics._
import kamon.module.{Module, ModuleFactory, ScheduledAction}
import kamon.tag.TagSet
import kamon.util.Filter
import oshi.SystemInfo
import oshi.hardware.CentralProcessor.TickType

import java.time.{Duration, Instant}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

/**
  * Collects CPU, Memory, Swap, Storage and Network metrics. The metrics collection is split into two groups: frequent
  * and infrequent collector; the frequent collector records metrics that are highly volatile and do not accumulate over
  * time, like the CPU usage, while the infrequent collector focuses on metrics that can be updated less frequently like
  * swap and memory usage and cumulative metrics like network and storage usage.
  */
class HostMetricsCollector(ec: ExecutionContext) extends ScheduledAction {
  private val _configPath = "kamon.instrumentation.system.host"
  private var _lastInfrequentTick = Instant.now()
  @volatile private var _settings: HostMetricsCollector.Settings = readSettings(Kamon.config())

  private val _frequentCollector = new FrequentCollectionTask
  private val _infrequentCollector = new InfrequentCollectionTask

  override def run(): Unit = {
    _frequentCollector.run()

    if (Duration.between(_lastInfrequentTick, Instant.now()).getSeconds() >= 10) {
      _infrequentCollector.run()
      _lastInfrequentTick = Instant.now()
    }
  }

  override def stop(): Unit = {
    _frequentCollector.cleanup()
    _infrequentCollector.cleanup()
  }

  override def reconfigure(newConfig: Config): Unit =
    _settings = readSettings(newConfig)

  private def readSettings(config: Config): HostMetricsCollector.Settings = {
    val hostConfig = config.getConfig(_configPath)

    HostMetricsCollector.Settings(
      Filter.from(hostConfig.getConfig("network.tracked-interfaces")),
      Filter.from(hostConfig.getConfig("storage.tracked-mount-types"))
    )
  }

  trait CollectionTask {
    def run(): Unit
    def cleanup(): Unit
  }

  private class FrequentCollectionTask extends CollectionTask {
    private val _defaultTags = TagSet.of("component", "host")
    private val _systemInfo = new SystemInfo()
    private val _hal = _systemInfo.getHardware()
    private val _cpuInstruments = new CpuInstruments(_defaultTags)
    private var _prevCpuLoadTicks: Array[Long] = Array.ofDim(0)

    def run(): Unit = {
      recordCpuUsage()
    }

    def cleanup(): Unit = {
      _cpuInstruments.remove()
    }

    private def recordCpuUsage(): Unit = {
      if (_prevCpuLoadTicks.length > 0) {
        val previousTicks = _prevCpuLoadTicks
        val currentTicks = _hal.getProcessor().getSystemCpuLoadTicks()

        val user = ticksDiff(previousTicks, currentTicks, TickType.USER)
        val nice = ticksDiff(previousTicks, currentTicks, TickType.NICE)
        val system = ticksDiff(previousTicks, currentTicks, TickType.SYSTEM)
        val idle = ticksDiff(previousTicks, currentTicks, TickType.IDLE)
        val iowait = ticksDiff(previousTicks, currentTicks, TickType.IOWAIT)
        val irq = ticksDiff(previousTicks, currentTicks, TickType.IRQ)
        val softirq = ticksDiff(previousTicks, currentTicks, TickType.SOFTIRQ)
        val steal = ticksDiff(previousTicks, currentTicks, TickType.STEAL)
        val total = user + nice + system + idle + iowait + irq + softirq + steal

        def toPercent(value: Long): Long = ((100d * value.toDouble) / total.toDouble).toLong

        _cpuInstruments.user.record(toPercent(user))
        _cpuInstruments.system.record(toPercent(system))
        _cpuInstruments.iowait.record(toPercent(iowait))
        _cpuInstruments.idle.record(toPercent(idle))
        _cpuInstruments.stolen.record(toPercent(steal))
        _cpuInstruments.combined.record(toPercent(user + system + nice + iowait))
        _prevCpuLoadTicks = currentTicks

      } else {
        _prevCpuLoadTicks = _hal.getProcessor().getSystemCpuLoadTicks()
      }
    }

    private def ticksDiff(previous: Array[Long], current: Array[Long], tickType: TickType): Long =
      math.max(current(tickType.getIndex) - previous(tickType.getIndex), 0L)

  }

  private class InfrequentCollectionTask extends CollectionTask {
    private val _defaultTags = TagSet.of("component", "host")
    private val _systemInfo = new SystemInfo()
    private val _hal = _systemInfo.getHardware()
    private val _os = _systemInfo.getOperatingSystem

    private val _memoryInstruments = new MemoryInstruments(_defaultTags)
    private val _swapInstruments = new SwapInstruments(_defaultTags)
    private val _loadAverageInstruments = new LoadAverageInstruments(_defaultTags)
    private val _fileSystemUsageInstruments = new StorageMountInstruments(_defaultTags)
    private val _fileSystemActivityInstruments = new StorageDeviceInstruments(_defaultTags)
    private val _networkActivityInstruments = new NetworkActivityInstruments(_defaultTags)

    def run(): Unit = {
      recordMemoryUsage()
      recordLoadAverage()
      recordStorageUsage()
      recordStorageActivity()
      recordNetworkActivity()
    }

    def cleanup(): Unit = {
      _memoryInstruments.remove()
      _swapInstruments.remove()
      _loadAverageInstruments.remove()
      _fileSystemUsageInstruments.remove()
      _fileSystemActivityInstruments.remove()
      _networkActivityInstruments.remove()
    }

    private def recordMemoryUsage(): Unit = {

      val memory = _hal.getMemory
      val totalMemory = memory.getTotal
      val availableMemory = memory.getAvailable
      val usedMemory = totalMemory - availableMemory

      _memoryInstruments.total.update(totalMemory)
      _memoryInstruments.free.update(availableMemory)
      _memoryInstruments.used.update(usedMemory)
      _memoryInstruments.usage.update(toPercent(usedMemory, totalMemory))

      val usedSwap = memory.getVirtualMemory.getSwapUsed
      val totalSwap = memory.getVirtualMemory.getSwapTotal
      _swapInstruments.total.update(totalSwap)
      _swapInstruments.used.update(usedSwap)
      _swapInstruments.usage.update(toPercent(usedSwap, totalSwap))
      _swapInstruments.free.update(totalSwap - usedSwap)
    }

    private def recordLoadAverage(): Unit = {
      val loadAverage = _hal.getProcessor.getSystemLoadAverage(3)
      if (loadAverage(0) >= 0d) _loadAverageInstruments.oneMinute.update(loadAverage(0))
      if (loadAverage(1) >= 0d) _loadAverageInstruments.fiveMinutes.update(loadAverage(1))
      if (loadAverage(2) >= 0d) _loadAverageInstruments.fifteenMinutes.update(loadAverage(2))
    }

    private def recordStorageUsage(): Unit = {
      val fileStores = _os.getFileSystem().getFileStores

      fileStores.asScala.foreach(fs => {
        if (_settings.trackedMounts.accept(fs.getType)) {
          val mountInstruments = _fileSystemUsageInstruments.mountInstruments(fs.getMount)
          val totalSpace = fs.getTotalSpace
          val usedSpace = totalSpace - fs.getUsableSpace

          mountInstruments.free.update(fs.getUsableSpace)
          mountInstruments.total.update(totalSpace)
          mountInstruments.used.update(usedSpace)
          mountInstruments.usage.update(toPercent(usedSpace, totalSpace))
        }
      })
    }

    private def toPercent(value: Long, total: Long): Long = ((100d * value.toDouble) / total.toDouble).toLong

    private def recordStorageActivity(): Unit = {
      val devices = _hal.getDiskStores
      devices.asScala.foreach(device => {
        if (!device.getPartitions.isEmpty) {
          val deviceInstruments = _fileSystemActivityInstruments.deviceInstruments(device.getName)
          deviceInstruments.reads.diff(device.getReads)
          deviceInstruments.writes.diff(device.getWrites)
          deviceInstruments.readBytes.diff(device.getReadBytes)
          deviceInstruments.writeBytes.diff(device.getWriteBytes)
        }
      })
    }

    private def recordNetworkActivity(): Unit = {
      val interfaces = _hal.getNetworkIFs()

      interfaces.asScala.foreach(interface => {
        if (_settings.trackedInterfaces.accept(interface.getName)) {
          interface.updateAttributes()
          val interfaceInstruments = _networkActivityInstruments.interfaceInstruments(interface.getName)
          interfaceInstruments.receivedBytes.diff(interface.getBytesRecv)
          interfaceInstruments.sentBytes.diff(interface.getBytesSent)
          interfaceInstruments.sentPackets.diff(interface.getPacketsSent)
          interfaceInstruments.receivedPackets.diff(interface.getPacketsRecv)
          interfaceInstruments.sendErrorPackets.diff(interface.getOutErrors)
          interfaceInstruments.receiveErrorPackets.diff(interface.getInErrors)
        }
      })
    }
  }

}

object HostMetricsCollector {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new HostMetricsCollector(settings.executionContext)
  }

  case class Settings(
    trackedInterfaces: Filter,
    trackedMounts: Filter
  )
}
