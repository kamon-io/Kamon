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
import org.hyperic.sigar.Sigar
import org.slf4j.Logger

import scala.concurrent.duration.FiniteDuration

class SigarMetricsUpdater(logger: Logger) {
  val sigar = new Sigar

  val sigarMetrics = List(
    CpuMetrics.register(sigar, logger),
    FileSystemMetrics.register(sigar, logger),
    LoadAverageMetrics.register(sigar, logger),
    MemoryMetrics.register(sigar, logger),
    NetworkMetrics.register(sigar, logger),
    ProcessCpuMetrics.register(sigar, logger),
    ULimitMetrics.register(sigar, logger)).flatten

  def updateMetrics(): Unit = {
    sigarMetrics.foreach(_.update())
  }

}


trait SigarMetric {
  def update(): Unit
}

object SigarSafeRunner {
  private val errorLogged = scala.collection.mutable.Set[String]()

  def runSafe[T](thunk: ⇒ T, defaultValue: ⇒ T, error: String, logger: Logger): T = {
    try thunk catch {
      case e: Exception ⇒
        if (!errorLogged.contains(error)) {
          errorLogged += error
          logger.warn(s"Couldn't get the metric [${error}]. Due to [${e.getMessage}]")
        }
        defaultValue
    }
  }
}

abstract class SigarMetricRecorderCompanion(metricName: String) {
  private val filterName = "system-metric"

  def register(sigar: Sigar, logger: Logger): Option[SigarMetric] = {
    val metricPrefix = s"$filterName.$metricName."
    if (Kamon.filter(filterName, metricName))
      Some(apply(sigar, metricPrefix, logger))
    else
      None
  }

  def apply(sigar: Sigar, metricPrefix: String, logger: Logger): SigarMetric
}
