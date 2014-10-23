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

case class HeapMetrics(name: String) extends MetricGroupIdentity {
  val category = HeapMetrics
}

object HeapMetrics extends MetricGroupCategory {
  val name = "heap"

  case object Used extends MetricIdentity { val name = "used-heap" }
  case object Max extends MetricIdentity { val name = "max-heap" }
  case object Committed extends MetricIdentity { val name = "committed-heap" }

  case class HeapMetricRecorder(used: Gauge, max: Gauge, committed: Gauge)
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
      Used -> used,
      Max -> max,
      Committed -> committed)
  }

  val Factory = HeapMetricGroupFactory
}

case object HeapMetricGroupFactory extends MetricGroupFactory {

  import HeapMetrics._
  import kamon.system.SystemMetricsExtension._

  def heap = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage

  type GroupRecorder = HeapMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jvm.heap")

    val usedHeapConfig = settings.getConfig("used")
    val maxHeapConfig = settings.getConfig("max")
    val committedHeapConfig = settings.getConfig("committed")

    new HeapMetricRecorder(
      Gauge.fromConfig(usedHeapConfig, system, Scale.Mega)(() ⇒ toMB(heap.getUsed)),
      Gauge.fromConfig(maxHeapConfig, system, Scale.Mega)(() ⇒ toMB(heap.getMax)),
      Gauge.fromConfig(committedHeapConfig, system, Scale.Mega)(() ⇒ toMB(heap.getCommitted)))
  }

}

