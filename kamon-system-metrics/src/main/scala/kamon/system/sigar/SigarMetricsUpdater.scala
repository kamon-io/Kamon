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

import akka.actor.{ Props, Actor }
import kamon.Kamon
import kamon.metric.instrument.InstrumentFactory
import kamon.metric._
import kamon.system.sigar.SigarMetricsUpdater.UpdateSigarMetrics
import org.hyperic.sigar.Sigar

import scala.concurrent.duration.FiniteDuration

class SigarMetricsUpdater(refreshInterval: FiniteDuration) extends Actor {
  val sigar = new Sigar
  val metricsExtension = Kamon.metrics

  val sigarMetrics = List(
    CpuMetrics.register(sigar, metricsExtension),
    FileSystemMetrics.register(sigar, metricsExtension),
    LoadAverageMetrics.register(sigar, metricsExtension),
    MemoryMetrics.register(sigar, metricsExtension),
    NetworkMetrics.register(sigar, metricsExtension),
    ProcessCpuMetrics.register(sigar, metricsExtension))

  val refreshSchedule = context.system.scheduler.schedule(refreshInterval, refreshInterval, self, UpdateSigarMetrics)(context.dispatcher)

  def receive = {
    case UpdateSigarMetrics ⇒ updateMetrics()
  }

  def updateMetrics(): Unit = {
    sigarMetrics.foreach(_.update())
  }

  override def postStop(): Unit = {
    refreshSchedule.cancel()
    super.postStop()
  }
}

object SigarMetricsUpdater {
  def props(refreshInterval: FiniteDuration): Props =
    Props(new SigarMetricsUpdater((refreshInterval)))

  case object UpdateSigarMetrics
}

trait SigarMetric extends EntityRecorder {
  def update(): Unit
}

abstract class SigarMetricRecorderCompanion(metricName: String) {
  def register(sigar: Sigar, metricsExtension: MetricsModule): SigarMetric =
    metricsExtension.entity(EntityRecorderFactory("system-metric", apply(sigar, _)), metricName)

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory): SigarMetric
}

