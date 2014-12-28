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

case class NonHeapMetrics(name: String) extends MetricGroupIdentity {
  val category = NonHeapMetrics
}

object NonHeapMetrics extends MetricGroupCategory {
  val name = "non-heap"

  case object Used extends MetricIdentity { val name = "used" }
  case object Max extends MetricIdentity { val name = "max" }
  case object Committed extends MetricIdentity { val name = "committed" }

  case class NonHeapMetricRecorder(used: Gauge, max: Gauge, committed: Gauge)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      NonHeapMetricSnapshot(used.collect(context), max.collect(context), committed.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class NonHeapMetricSnapshot(used: Histogram.Snapshot, max: Histogram.Snapshot, committed: Histogram.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = NonHeapMetricSnapshot

    def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType = {
      NonHeapMetricSnapshot(used.merge(that.used, context), max.merge(that.max, context), committed.merge(that.committed, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Used -> used,
      Max -> max,
      Committed -> committed)
  }

  val Factory = NonHeapMetricGroupFactory
}

case object NonHeapMetricGroupFactory extends MetricGroupFactory {

  import NonHeapMetrics._
  import kamon.system.SystemMetricsExtension._

  def nonHeap = ManagementFactory.getMemoryMXBean.getNonHeapMemoryUsage

  type GroupRecorder = NonHeapMetricRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jvm.non-heap")

    val usedNonHeapConfig = settings.getConfig("used")
    val maxNonHeapConfig = settings.getConfig("max")
    val committedNonHeapConfig = settings.getConfig("committed")

    new NonHeapMetricRecorder(
      Gauge.fromConfig(usedNonHeapConfig, system, Scale.Mega)(() ⇒ toMB(nonHeap.getUsed)),
      Gauge.fromConfig(maxNonHeapConfig, system, Scale.Mega)(() ⇒ toMB(nonHeap.getMax)),
      Gauge.fromConfig(committedNonHeapConfig, system, Scale.Mega)(() ⇒ toMB(nonHeap.getCommitted)))
  }
}

