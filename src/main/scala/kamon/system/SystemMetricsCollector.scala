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

import java.io.File
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

import kamon.Kamon
import kamon.sigar.SigarProvisioner
import kamon.system.custom.ContextSwitchesMetrics
import kamon.system.jmx.{ClassLoadingMetrics, GarbageCollectionMetrics, _}
import kamon.system.sigar.SigarMetricsUpdater
import org.hyperic.sigar.Sigar

object SystemMetricsCollector {
//
  private var scheduledCollections: Seq[ScheduledFuture[_]] = Seq.empty

  def startCollecting = {
    SystemMetrics.FilterName
    scheduledCollections = Seq(
      // OS Metrics collected with Sigar
      if (SystemMetrics.sigarEnabled) Some(collectSigar) else None,

      // If we are in Linux, add ContextSwitchesMetrics as well.
      if (SystemMetrics.sigarEnabled && isLinux) Some(collectContextSwitches) else None,

      // JMX Metrics
      if (SystemMetrics.jmxEnabled) Some(collectJMX) else None
    ).flatten
  }

  def stopCollecting(): Unit = {
    scheduledCollections.foreach(_.cancel(false))
    scheduledCollections = Seq.empty
  }

  def isLinux: Boolean =
    System.getProperty("os.name").indexOf("Linux") != -1


  private def collectSigar: ScheduledFuture[_] = {
    SigarProvisioner.provision(new File(SystemMetrics.sigarFolder))
    val sigarMetricsUpdater = new SigarMetricsUpdater(SystemMetrics.logger)

    Kamon.scheduler().scheduleAtFixedRate(sigarMetricsUpdater, 0L, SystemMetrics.sigarRefreshInterval.toMillis, MILLISECONDS)
  }

  private def collectJMX: ScheduledFuture[_] = {
    val jmxMetricsUpdater = new JmxMetricsUpdater()

    Kamon.scheduler().scheduleAtFixedRate(jmxMetricsUpdater, 0L, SystemMetrics.jmxRefreshInterval.toMillis, MILLISECONDS)
  }


  private def collectContextSwitches: ScheduledFuture[_] = {
    val pid = (new Sigar).getPid
    val contextSwitchesRecorder = new ContextSwitchesMetrics(pid, SystemMetrics.logger)
    val cxSwitchSchedule = new Runnable {
      override def run(): Unit = contextSwitchesRecorder.update()
    }
    Kamon.scheduler().scheduleAtFixedRate(
      cxSwitchSchedule, SystemMetrics.contextSwitchesRefreshInterval.toMillis,
      SystemMetrics.contextSwitchesRefreshInterval.toMillis,
      MILLISECONDS
    )
  }
}

