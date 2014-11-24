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

package kamon.jdbc.metric

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric.{ CollectionContext, MetricGroupCategory, MetricGroupFactory, MetricGroupIdentity, MetricGroupRecorder, MetricGroupSnapshot, MetricIdentity, MetricSnapshot }
import kamon.metric.instrument.{ Counter, Histogram }

case class StatementsMetrics(name: String) extends MetricGroupIdentity {
  val category = StatementsMetrics
}

object StatementsMetrics extends MetricGroupCategory {
  val name = "jdbc-statements"

  case object Writes extends MetricIdentity { val name = "writes" }
  case object Reads extends MetricIdentity { val name = "reads" }
  case object Slows extends MetricIdentity { val name = "slow-queries" }
  case object Errors extends MetricIdentity { val name = "errors" }

  case class StatementsMetricsRecorder(writes: Histogram, reads: Histogram, slow: Counter, errors: Counter)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot = {
      StatementsMetricsSnapshot(writes.collect(context), reads.collect(context), slow.collect(context), errors.collect(context))
    }

    def cleanup: Unit = {}
  }

  case class StatementsMetricsSnapshot(writes: Histogram.Snapshot, reads: Histogram.Snapshot, slows: Counter.Snapshot, errors: Counter.Snapshot)
      extends MetricGroupSnapshot {

    type GroupSnapshotType = StatementsMetricsSnapshot

    def merge(that: StatementsMetricsSnapshot, context: CollectionContext): GroupSnapshotType = {
      StatementsMetricsSnapshot(writes.merge(that.writes, context), reads.merge(that.reads, context), slows.merge(that.slows, context), errors.merge(that.errors, context))
    }

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      Writes -> writes,
      Reads -> reads,
      Slows -> slows,
      Reads -> errors)
  }

  val Factory = StatementsMetricsGroupFactory
}

case object StatementsMetricsGroupFactory extends MetricGroupFactory {
  import kamon.jdbc.metric.StatementsMetrics._

  type GroupRecorder = StatementsMetricsRecorder

  def create(config: Config, system: ActorSystem): GroupRecorder = {
    val settings = config.getConfig("precision.jdbc.statements")

    val writesConfig = settings.getConfig("writes")
    val readsConfig = settings.getConfig("reads")

    new StatementsMetricsRecorder(
      Histogram.fromConfig(writesConfig),
      Histogram.fromConfig(readsConfig),
      Counter(),
      Counter())
  }
}

