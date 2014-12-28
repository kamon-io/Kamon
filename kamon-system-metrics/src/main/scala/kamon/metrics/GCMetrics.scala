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

import java.lang.management.GarbageCollectorMXBean

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.Histogram

case class GCMetrics(name: String) extends MetricGroupIdentity {
  val category = GCMetrics
}

object GCMetrics extends MetricGroupCategory {
  val name = "gc"

  case object CollectionCount extends MetricIdentity { val name = "collection-count" }
  case object CollectionTime extends MetricIdentity { val name = "collection-time" }

  case class GCMetricRecorder(count: Histogram, time: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      GCMetricSnapshot(count.collect(context), time.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class GCMetricSnapshot(count: Histogram.Snapshot, time: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = GCMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      GCMetricSnapshot(count.merge(that.count, context), time.merge(that.time, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      CollectionCount -> count,
      CollectionTime -> time)
  }

  def Factory(gc: GarbageCollectorMXBean) = GCMetricGroupFactory(gc)
}

case class GCMetricGroupFactory(gc: GarbageCollectorMXBean) extends MetricGroupFactory {
  import GCMetrics._

  type GroupRecorder = GCMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jvm.gc")

    val countConfig = settings.getConfig("count")
    val timeConfig = settings.getConfig("time")

    new GCMetricRecorder(
      Histogram.fromConfig(countConfig),
      Histogram.fromConfig(timeConfig))
  }
}