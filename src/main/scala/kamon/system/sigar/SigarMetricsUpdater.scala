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
import akka.event.{ Logging, NoLogging, LoggingAdapter }
import kamon.Kamon
import kamon.metric.instrument.InstrumentFactory
import kamon.metric._
import kamon.system.sigar.SigarMetricsUpdater.UpdateSigarMetrics
import org.hyperic.sigar.Sigar

import scala.concurrent.duration.FiniteDuration

class SigarMetricsUpdater(refreshInterval: FiniteDuration) extends Actor {
  val sigar = new Sigar
  val metricsExtension = Kamon.metrics
  val logger = Logging(context.system, this)

  val sigarMetrics = List(
    CpuMetrics.register(sigar, metricsExtension, logger),
    FileSystemMetrics.register(sigar, metricsExtension, logger),
    LoadAverageMetrics.register(sigar, metricsExtension, logger),
    MemoryMetrics.register(sigar, metricsExtension, logger),
    NetworkMetrics.register(sigar, metricsExtension, logger),
    ProcessCpuMetrics.register(sigar, metricsExtension, logger),
    ULimitMetrics.register(sigar, metricsExtension, logger)).flatten

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
    Props(new SigarMetricsUpdater(refreshInterval))

  case object UpdateSigarMetrics
}

trait SigarMetric extends EntityRecorder {
  def update(): Unit
}

object SigarSafeRunner {
  private val errorLogged = scala.collection.mutable.Set[String]()

  def runSafe[T](thunk: ⇒ T, defaultValue: ⇒ T, error: String, logger: LoggingAdapter): T = {
    try thunk catch {
      case e: Exception ⇒
        if (!errorLogged.contains(error)) {
          errorLogged += error
          logger.warning("Couldn't get the metric [{}]. Due to [{}]", error, e.getMessage)
        }
        defaultValue
    }
  }
}

abstract class SigarMetricRecorderCompanion(metricName: String) {
  def register(sigar: Sigar, metricsExtension: MetricsModule, logger: LoggingAdapter = NoLogging): Option[SigarMetric] =
    if (metricsExtension.shouldTrack(metricName, "system-metric"))
      Some(metricsExtension.entity(EntityRecorderFactory("system-metric", apply(sigar, _)), metricName))
    else
      None

  def apply(sigar: Sigar, instrumentFactory: InstrumentFactory, logger: LoggingAdapter = NoLogging): SigarMetric
}
