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

case class GCMetrics(name: String) extends MetricGroupIdentity {
  val category = GCMetrics
}

object GCMetrics extends MetricGroupCategory {
  val name = "gc"

  case object CollectionCount extends MetricIdentity { val name, tag = "collection-count" }
  case object CollectionTime extends MetricIdentity { val name, tag = "collection-time" }

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
      (CollectionCount -> count),
      (CollectionTime -> time))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = GCMetricRecorder

    def create(config: Config, system: ActorSystem): GroupRecorder = {
      val settings = config.getConfig("precision.jvm")

      val countConfig = settings.getConfig("gc-count")
      val timeConfig = settings.getConfig("gc-time")

      new GCMetricRecorder(
        Histogram.fromConfig(countConfig),
        Histogram.fromConfig(timeConfig))
    }
  }
}

