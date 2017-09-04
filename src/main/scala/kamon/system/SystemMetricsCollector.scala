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
import java.util.concurrent.{ScheduledFuture, TimeUnit}

import kamon.system.custom.ContextSwitchesMetrics
import kamon.system.jmx.{ClassLoadingMetrics, _}
import kamon.Kamon
import kamon.sigar.SigarProvisioner
import kamon.system.sigar.SigarMetricsUpdater
import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory



object SystemMetricsCollector {

  private val logger = LoggerFactory.getLogger("kamon.metrics.SystemMetrics")

  val config                          = Kamon.config().getConfig("kamon.system-metrics")

  val sigarFolder                     = config.getString("sigar-native-folder")
  val sigarRefreshInterval            = config.getDuration("sigar-metrics-refresh-interval")
  val sigarEnabled                    = config.getBoolean("sigar-enabled")
  val jmxEnabled                      = config.getBoolean("jmx-enabled")
  val contextSwitchesRefreshInterval  = config.getDuration("context-switches-refresh-interval")

  private var scheduledCollections: Seq[ScheduledFuture[_]] = Seq.empty

  def startCollecting = {
    scheduledCollections = Seq(
      // OS Metrics collected with Sigar
      if (sigarEnabled) Some(collectSigar) else None,

      // If we are in Linux, add ContextSwitchesMetrics as well.
      if (sigarEnabled && isLinux) Some(collectContextSwitches) else None,

      // JMX Metrics
      if (jmxEnabled) Some(collectJMX) else None
    ).flatten
  }

  def stopCollecting = {
    scheduledCollections.foreach(_.cancel(false))
    scheduledCollections = Seq.empty
  }

  def isLinux: Boolean =
    System.getProperty("os.name").indexOf("Linux") != -1


  private def collectSigar: ScheduledFuture[_] = {
    SigarProvisioner.provision(new File(sigarFolder))

    val sigarMetricsUpdater = new SigarMetricsUpdater(logger)

    val sigarUpdaterSchedule = new Runnable {
      override def run(): Unit = sigarMetricsUpdater.updateMetrics()
    }
    Kamon.scheduler().scheduleAtFixedRate(
      sigarUpdaterSchedule,
      sigarRefreshInterval.toMillis,
      sigarRefreshInterval.toMillis,
      TimeUnit.MILLISECONDS
    )
  }

  private def collectContextSwitches: ScheduledFuture[_] = {
    val pid = (new Sigar).getPid
    val contextSwitchesRecorder = new ContextSwitchesMetrics(pid, logger)
    val cxSwitchSchedule = new Runnable {
      override def run(): Unit = contextSwitchesRecorder.update()
    }
    Kamon.scheduler().scheduleAtFixedRate(
      cxSwitchSchedule, contextSwitchesRefreshInterval.toMillis,
      contextSwitchesRefreshInterval.toMillis,
      TimeUnit.MILLISECONDS
    )
  }

  private def collectJMX: ScheduledFuture[_] = {
    val enabledMetrics = Seq(
      MemoryUsageMetrics.register(),
      ClassLoadingMetrics.register(),
      ThreadsMetrics.register()
    ).flatten ++ GarbageCollectionMetrics.register()

    val jmxMetricSchedule = new Runnable {
      override def run(): Unit = enabledMetrics.foreach(_.update())
    }

    Kamon.scheduler().scheduleAtFixedRate(
      jmxMetricSchedule,
      sigarRefreshInterval.toMillis,
      sigarRefreshInterval.toMillis,
      TimeUnit.MILLISECONDS
    )

  }
}

