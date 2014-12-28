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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.Histogram

case class DiskMetrics(name: String) extends MetricGroupIdentity {
  val category = DiskMetrics
}

object DiskMetrics extends MetricGroupCategory {
  val name = "disk"

  case object Reads extends MetricIdentity { val name = "reads" }
  case object Writes extends MetricIdentity { val name = "writes" }
  case object Queue extends MetricIdentity { val name = "queue" }
  case object ServiceTime extends MetricIdentity { val name = "service-time" }

  case class DiskMetricsRecorder(reads: Histogram, writes: Histogram, queue: Histogram, serviceTime: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      DiskMetricsSnapshot(reads.collect(context), writes.collect(context), queue.collect(context), serviceTime.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class DiskMetricsSnapshot(reads: Histogram.Snapshot, writes: Histogram.Snapshot, queue: Histogram.Snapshot, serviceTime: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = DiskMetricsSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      DiskMetricsSnapshot(reads.merge(that.reads, context), writes.merge(that.writes, context), queue.merge(that.queue, context), serviceTime.merge(that.serviceTime, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Reads -> reads,
      Writes -> writes,
      Queue -> queue,
      ServiceTime -> serviceTime)
  }

  val Factory = DiskMetricGroupFactory
}

case object DiskMetricGroupFactory extends MetricGroupFactory {

  import DiskMetrics._

  type GroupRecorder = DiskMetricsRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.system.disk")

    val readsDiskConfig = settings.getConfig("reads")
    val writesDiskConfig = settings.getConfig("writes")
    val queueDiskConfig = settings.getConfig("queue")
    val serviceTimeDiskConfig = settings.getConfig("service-time")

    new DiskMetricsRecorder(
      Histogram.fromConfig(readsDiskConfig),
      Histogram.fromConfig(writesDiskConfig),
      Histogram.fromConfig(queueDiskConfig),
      Histogram.fromConfig(serviceTimeDiskConfig))
  }
}

