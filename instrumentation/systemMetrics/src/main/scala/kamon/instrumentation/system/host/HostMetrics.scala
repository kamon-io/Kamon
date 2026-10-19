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

package kamon.instrumentation.system.host

import kamon.Kamon
import kamon.instrumentation.system.host.HostMetrics.StorageDeviceInstruments.DeviceInstruments
import kamon.instrumentation.system.host.HostMetrics.StorageMountInstruments.MountInstruments
import kamon.instrumentation.system.host.HostMetrics.NetworkActivityInstruments.InterfaceInstruments
import kamon.metric.{Counter, Gauge, Histogram, InstrumentGroup, MeasurementUnit}
import kamon.tag.TagSet

import scala.collection.mutable

object HostMetrics {

  val CpuUsage = Kamon.histogram(
    name = "host.cpu.usage",
    description = "Samples the CPU usage percentage",
    unit = MeasurementUnit.percentage
  )

  val MemoryUsed = Kamon.gauge(
    name = "host.memory.used",
    description = "Tracks the amount of used memory",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryUsage = Kamon.gauge(
    name = "host.memory.usage",
    description = "Tracks the percetange of memory used",
    unit = MeasurementUnit.percentage
  )

  val MemoryFree = Kamon.gauge(
    name = "host.memory.free",
    description = "Tracks the amount of free memory",
    unit = MeasurementUnit.information.bytes
  )

  val MemoryTotal = Kamon.gauge(
    name = "host.memory.total",
    description = "Tracks the total memory available",
    unit = MeasurementUnit.information.bytes
  )

  val SwapUsed = Kamon.gauge(
    name = "host.swap.used",
    description = "Tracks the used Swap space",
    unit = MeasurementUnit.information.bytes
  )

  val SwapUsage = Kamon.gauge(
    name = "host.swap.usage",
    description = "Tracks the percentage of swap used",
    unit = MeasurementUnit.percentage
  )

  val SwapFree = Kamon.gauge(
    name = "host.swap.free",
    description = "Tracks the free Swap space",
    unit = MeasurementUnit.information.bytes
  )

  val SwapTotal = Kamon.gauge(
    name = "host.swap.total",
    description = "Tracks the total Swap space",
    unit = MeasurementUnit.information.bytes
  )

  val LoadAverage = Kamon.gauge(
    name = "host.load.average",
    description = "Tracks the system load average"
  )

  val FileSystemMountSpaceUsed = Kamon.gauge(
    name = "host.storage.mount.space.used",
    description = "Tracks the used space on a file system mount/volume",
    unit = MeasurementUnit.information.bytes
  )

  val FileSystemMountSpaceUsage = Kamon.gauge(
    name = "host.storage.mount.space.usage",
    description = "Tracks the usage of space on a file system mount/volume",
    unit = MeasurementUnit.percentage
  )

  val FileSystemMountSpaceFree = Kamon.gauge(
    name = "host.storage.mount.space.free",
    description = "Tracks the free space on a file system mount/volume",
    unit = MeasurementUnit.information.bytes
  )

  val FileSystemMountSpaceTotal = Kamon.gauge(
    name = "host.storage.mount.space.total",
    description = "Tracks the total space on a file system mount/volume",
    unit = MeasurementUnit.information.bytes
  )

  val StorageDeviceRead = Kamon.counter(
    name = "host.storage.device.data.read",
    description = "Counts the amount of bytes that have been read from a storage device",
    unit = MeasurementUnit.information.bytes
  )

  val StorageDeviceWrite = Kamon.counter(
    name = "host.storage.device.data.write",
    description = "Counts the amount of bytes that have been written to a storage device",
    unit = MeasurementUnit.information.bytes
  )

  val StorageDeviceReadOps = Kamon.counter(
    name = "host.storage.device.ops.read",
    description = "Counts the number of read operations executed on a storage device"
  )

  val StorageDeviceWriteOps = Kamon.counter(
    name = "host.storage.device.ops.write",
    description = "Counts the number of write operations executed on a storage device"
  )

  val NetworkPacketsRead = Kamon.counter(
    name = "host.network.packets.read.total",
    description = "Counts how many packets have been read from a network interface"
  )

  val NetworkPacketsWrite = Kamon.counter(
    name = "host.network.packets.write.total",
    description = "Counts how many packets have been written to a network interface"
  )

  val NetworkPacketsReadFailed = Kamon.counter(
    name = "host.network.packets.read.failed",
    description = "Counts how many packets failed to be read from a network interface"
  )

  val NetworkPacketsWriteFailed = Kamon.counter(
    name = "host.network.packets.write.failed",
    description = "Counts how many packets failed to be written to a network interface"
  )

  val NetworkDataRead = Kamon.counter(
    name = "host.network.data.read",
    description = "Counts how many bytes have been read from a network interface",
    unit = MeasurementUnit.information.bytes
  )

  val NetworkDataWrite = Kamon.counter(
    name = "host.network.data.write",
    description = "Counts how many bytes have been written to a network interface",
    unit = MeasurementUnit.information.bytes
  )

  class CpuInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val user = register(CpuUsage, "mode", "user")
    val system = register(CpuUsage, "mode", "system")
    val iowait = register(CpuUsage, "mode", "wait")
    val idle = register(CpuUsage, "mode", "idle")
    val stolen = register(CpuUsage, "mode", "stolen")
    val combined = register(CpuUsage, "mode", "combined")
  }

  class MemoryInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val used = register(MemoryUsed)
    val free = register(MemoryFree)
    val usage = register(MemoryUsage)
    val total = register(MemoryTotal)
  }

  class SwapInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val used = register(SwapUsed)
    val usage = register(SwapUsage)
    val free = register(SwapFree)
    val total = register(SwapTotal)
  }

  class LoadAverageInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val oneMinute = register(LoadAverage, "period", "1m")
    val fiveMinutes = register(LoadAverage, "period", "5m")
    val fifteenMinutes = register(LoadAverage, "period", "15m")
  }

  class StorageMountInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    // It is ok to use mutable, not-synchronized collections here because they will only be accessed from one Thread
    // at a time and that Thread is always the same Thread.
    private val _mountsCache = mutable.Map.empty[String, MountInstruments]

    def mountInstruments(mountName: String): MountInstruments =
      _mountsCache.getOrElseUpdate(
        mountName, {
          val mount = TagSet.of("mount", mountName)

          MountInstruments(
            register(FileSystemMountSpaceUsed, mount),
            register(FileSystemMountSpaceUsage, mount),
            register(FileSystemMountSpaceFree, mount),
            register(FileSystemMountSpaceTotal, mount)
          )
        }
      )
  }

  object StorageMountInstruments {

    case class MountInstruments(
      used: Gauge,
      usage: Gauge,
      free: Gauge,
      total: Gauge
    )

  }

  class StorageDeviceInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    // It is ok to use mutable, not-synchronized collections here because they will only be accessed from one Thread
    // at a time and that Thread is always the same Thread.
    private val _deviceInstrumentsCache = mutable.Map.empty[String, DeviceInstruments]

    def deviceInstruments(deviceName: String): DeviceInstruments =
      _deviceInstrumentsCache.getOrElseUpdate(
        deviceName, {
          val device = TagSet.of("device", deviceName)

          DeviceInstruments(
            DiffCounter(register(StorageDeviceReadOps, device)),
            DiffCounter(register(StorageDeviceRead, device)),
            DiffCounter(register(StorageDeviceWriteOps, device)),
            DiffCounter(register(StorageDeviceWrite, device))
          )
        }
      )
  }

  object StorageDeviceInstruments {

    case class DeviceInstruments(
      reads: DiffCounter,
      readBytes: DiffCounter,
      writes: DiffCounter,
      writeBytes: DiffCounter
    )

  }

  class NetworkActivityInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    // It is ok to use mutable, not-synchronized collections here because they will only be accessed from one Thread
    // at a time and that Thread is always the same Thread.
    private val _interfaceCache = mutable.Map.empty[String, InterfaceInstruments]

    def interfaceInstruments(interfaceName: String): InterfaceInstruments =
      _interfaceCache.getOrElseUpdate(
        interfaceName, {
          val interface = TagSet.of("interface", interfaceName)

          InterfaceInstruments(
            DiffCounter(register(NetworkDataRead, interface)),
            DiffCounter(register(NetworkPacketsRead, interface)),
            DiffCounter(register(NetworkPacketsReadFailed, interface)),
            DiffCounter(register(NetworkDataWrite, interface)),
            DiffCounter(register(NetworkPacketsWrite, interface)),
            DiffCounter(register(NetworkPacketsWriteFailed, interface))
          )
        }
      )
  }

  object NetworkActivityInstruments {
    case class InterfaceInstruments(
      receivedBytes: DiffCounter,
      receivedPackets: DiffCounter,
      receiveErrorPackets: DiffCounter,
      sentBytes: DiffCounter,
      sentPackets: DiffCounter,
      sendErrorPackets: DiffCounter
    )
  }

  /**
    * A modified Counter that keeps track of a monotonically increasing value and only records the difference between
    * the current and previous value on the target counter.
    */
  case class DiffCounter(counter: Counter) {
    private var _previous = 0L

    def diff(current: Long): Unit = {
      if (_previous > 0L) {
        val delta = current - _previous
        if (delta > 0)
          counter.increment(delta)

      }

      _previous = current
    }
  }

}
