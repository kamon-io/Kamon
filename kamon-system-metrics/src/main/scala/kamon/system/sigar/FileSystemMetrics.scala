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

import kamon.metric.GenericEntityRecorder
import kamon.metric.instrument.{ Memory, InstrumentFactory }
import org.hyperic.sigar.{ DiskUsage, FileSystem, Sigar }
import scala.util.Try

/**
 *  Disk usage metrics, as reported by Sigar:
 *    - readBytes: Total number of physical disk reads.
 *    - writesBytes:  Total number of physical disk writes.
 */
class FileSystemMetrics(sigar: Sigar, instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) with SigarMetric {
  val reads = DiffRecordingHistogram(histogram("file-system-reads", Memory.Bytes))
  val writes = DiffRecordingHistogram(histogram("file-system-writes", Memory.Bytes))

  val fileSystems = sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDevName).toSet

  def sumOfAllFileSystems(sigar: Sigar, thunk: DiskUsage ⇒ Long): Long = Try {
    fileSystems.map(i ⇒ thunk(sigar.getDiskUsage(i))).fold(0L)(_ + _)
  } getOrElse 0L

  def update(): Unit = {
    reads.record(sumOfAllFileSystems(sigar, _.getReadBytes))
    writes.record(sumOfAllFileSystems(sigar, _.getWriteBytes))
  }
}

object FileSystemMetrics extends SigarMetricRecorderCompanion("file-system") {
  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): FileSystemMetrics =
    new FileSystemMetrics(sigar, instrumentFactory)
}
