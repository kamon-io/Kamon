/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.system.host

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import kamon.util.DifferentialSource
import org.hyperic.sigar.{DiskUsage, FileSystem, Sigar}
import org.slf4j.Logger

import scala.util.Try

/**
 *  Disk usage metrics, as reported by Sigar:
 *    - readBytes: Total number of physical disk reads.
 *    - writesBytes:  Total number of physical disk writes.
 */
object FileSystemMetrics extends MetricBuilder("host.file-system") with SigarMetricBuilder {
  def build(sigar: Sigar, metricName: String,logger: Logger) = new Metric {
    import kamon.system.host.SigarSafeRunner.runSafe

    val fileSystems = runSafe(sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDevName).toSet, Set.empty[String], "file-system", logger)
    val mountPoints = runSafe(sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDirName).toSet, Set.empty[String], "file-system", logger)

    val fileSystemActivityMetric = Kamon.counter("host.file-system.activity", MeasurementUnit.information.bytes)
    val fileSystemUsageMetric = Kamon.gauge("host.file-system.usage", MeasurementUnit.information.bytes)

    val fileSystemUsageRecorders = mountPoints.map(fs => {
      (fs, fileSystemUsageMetric.refine("component" -> "system-metrics", "fs" -> fs))
    })

    val readsMetric   = fileSystemActivityMetric.refine(Map("component" -> "system-metrics", "operation" -> "read"))
    val writesMetric  = fileSystemActivityMetric.refine(Map("component" -> "system-metrics", "operation" -> "write"))
    val fileSystemReadsSource = DifferentialSource(() => { sumOfAllFileSystems(sigar, _.getReadBytes) })
    val fileSystemWritesSource = DifferentialSource(() => { sumOfAllFileSystems(sigar, _.getWriteBytes) })

    def sumOfAllFileSystems(sigar: Sigar, thunk: DiskUsage ⇒ Long): Long = Try {
      fileSystems.map(i ⇒ thunk(sigar.getDiskUsage(i))).fold(0L)(_ + _)
    } getOrElse 0L

    override def update(): Unit = {
      readsMetric.increment(fileSystemReadsSource.get())
      writesMetric.increment(fileSystemWritesSource.get())

      fileSystemUsageRecorders.foreach {
        case (fs, gauge) =>
          Try((sigar.getFileSystemUsage(fs).getUsePercent() * 100).round).foreach(fileSystemUsage => {
            gauge.set(fileSystemUsage)
          })
      }
    }
  }
}
