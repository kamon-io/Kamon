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

import kamon.Kamon
import kamon.metric.MeasurementUnit
import org.hyperic.sigar.{DiskUsage, FileSystem, Sigar}
import org.slf4j.Logger

import scala.util.Try

/**
 *  Disk usage metrics, as reported by Sigar:
 *    - readBytes: Total number of physical disk reads.
 *    - writesBytes:  Total number of physical disk writes.
 */
class FileSystemMetrics(sigar: Sigar, metricPrefix: String, logger: Logger) extends SigarMetric {
  import kamon.system.sigar.SigarSafeRunner._

  val reads   = DiffRecordingHistogram(Kamon.histogram("system-metric.file-system.reads", MeasurementUnit.information.bytes))
  val writes  = DiffRecordingHistogram(Kamon.histogram("system-metric.file-system.writes", MeasurementUnit.information.bytes))

  val fileSystems = runSafe(sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDevName).toSet, Set.empty[String], "file-system", logger)

  def sumOfAllFileSystems(sigar: Sigar, thunk: DiskUsage ⇒ Long): Long = Try {
    fileSystems.map(i ⇒ thunk(sigar.getDiskUsage(i))).fold(0L)(_ + _)
  } getOrElse 0L

  def update(): Unit = {
    reads.record(sumOfAllFileSystems(sigar, _.getReadBytes))
    writes.record(sumOfAllFileSystems(sigar, _.getWriteBytes))
  }
}

object FileSystemMetrics extends SigarMetricRecorderCompanion("file-system") {
  def apply(sigar: Sigar, metricPrefix: String,logger: Logger): FileSystemMetrics =
    new FileSystemMetrics(sigar, metricPrefix, logger)
}
