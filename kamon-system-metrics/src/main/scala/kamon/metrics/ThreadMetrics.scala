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
package kamon.metrics

import java.lang.management.ManagementFactory

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.{ Gauge, Histogram }

case class ThreadMetrics(name: String) extends MetricGroupIdentity {
  val category = ThreadMetrics
}

object ThreadMetrics extends MetricGroupCategory {
  val name = "thread"

  case object Damon extends MetricIdentity { val name = "daemon-count" }
  case object Count extends MetricIdentity { val name = "count" }
  case object Peak extends MetricIdentity { val name = "peak-count" }

  case class ThreadMetricRecorder(daemon: Gauge, count: Gauge, peak: Gauge)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      ThreadMetricSnapshot(daemon.collect(context), count.collect(context), peak.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class ThreadMetricSnapshot(daemon: Histogram.Snapshot, count: Histogram.Snapshot, peak: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = ThreadMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      ThreadMetricSnapshot(daemon.merge(that.daemon, context), count.merge(that.count, context), peak.merge(that.peak, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Damon -> daemon,
      Count -> count,
      Peak -> peak)
  }

  val Factory = ThreadMetricGroupFactory
}

case object ThreadMetricGroupFactory extends MetricGroupFactory {

  import ThreadMetrics._

  def threads = ManagementFactory.getThreadMXBean

  type GroupRecorder = ThreadMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jvm.thread")

    val daemonThreadConfig = settings.getConfig("daemon")
    val countThreadsConfig = settings.getConfig("count")
    val peakThreadsConfig = settings.getConfig("peak")

    new ThreadMetricRecorder(
      Gauge.fromConfig(daemonThreadConfig, system)(() ⇒ threads.getDaemonThreadCount.toLong),
      Gauge.fromConfig(countThreadsConfig, system)(() ⇒ threads.getThreadCount.toLong),
      Gauge.fromConfig(peakThreadsConfig, system)(() ⇒ threads.getPeakThreadCount.toLong))
  }
}

