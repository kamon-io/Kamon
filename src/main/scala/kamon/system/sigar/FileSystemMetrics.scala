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

package kamon.system.sigar

import kamon.Kamon
import kamon.metric.MeasurementUnit
import kamon.system.{Metric, MetricBuilder, SigarMetricBuilder}
import org.hyperic.sigar.{DiskUsage, FileSystem, Sigar}
import org.slf4j.Logger

import scala.util.Try

/**
 *  Disk usage metrics, as reported by Sigar:
 *    - readBytes: Total number of physical disk reads.
 *    - writesBytes:  Total number of physical disk writes.
 */
object FileSystemMetrics extends MetricBuilder("file-system") with SigarMetricBuilder {
  def build(sigar: Sigar, metricPrefix: String,logger: Logger) = new Metric {
    import kamon.system.sigar.SigarSafeRunner.runSafe

    val readsMetric   = DiffRecordingHistogram(Kamon.histogram(s"$metricPrefix.reads", MeasurementUnit.information.bytes))
    val writesMetric  = DiffRecordingHistogram(Kamon.histogram(s"$metricPrefix.writes", MeasurementUnit.information.bytes))

    val fileSystems = runSafe(sigar.getFileSystemList.filter(_.getType == FileSystem.TYPE_LOCAL_DISK).map(_.getDevName).toSet, Set.empty[String], "file-system", logger)

    def sumOfAllFileSystems(sigar: Sigar, thunk: DiskUsage ⇒ Long): Long = Try {
      fileSystems.map(i ⇒ thunk(sigar.getDiskUsage(i))).fold(0L)(_ + _)
    } getOrElse 0L

    override def update(): Unit = {
      readsMetric.record(sumOfAllFileSystems(sigar, _.getReadBytes))
      writesMetric.record(sumOfAllFileSystems(sigar, _.getWriteBytes))
    }
  }
}
