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

case class HeapMetrics(name: String) extends MetricGroupIdentity {
  val category = GCMetrics
}

object HeapMetrics extends MetricGroupCategory {
  val name = "heap"

  case object Used extends MetricIdentity { val name, tag = "used-heap" }
  case object Max extends MetricIdentity { val name, tag = "max-heap" }
  case object Committed extends MetricIdentity { val name, tag = "committed-heap" }

  case class HeapMetricRecorder(used: Histogram, max: Histogram, committed: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      HeapMetricSnapshot(used.collect(context), max.collect(context), committed.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class HeapMetricSnapshot(used: Histogram.Snapshot, max: Histogram.Snapshot, committed: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = HeapMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      HeapMetricSnapshot(used.merge(that.used, context), max.merge(that.max, context), committed.merge(that.committed, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (Used -> used),
      (Max -> max),
      (Committed -> committed))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = HeapMetricRecorder

    def create(config: Config, system: ActorSystem): GroupRecorder = {
      val settings = config.getConfig("precision.jvm")

      val usedHeapConfig = settings.getConfig("used-heap")
      val maxHeapConfig = settings.getConfig("max-heap")
      val committedHeapConfig = settings.getConfig("committed-heap")

      new HeapMetricRecorder(
        Histogram.fromConfig(usedHeapConfig),
        Histogram.fromConfig(maxHeapConfig),
        Histogram.fromConfig(committedHeapConfig))
    }
  }
}

