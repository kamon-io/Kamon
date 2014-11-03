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

package kamon.metric

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric.instrument.{ Histogram, HdrHistogram }

case class DispatcherMetrics(name: String) extends MetricGroupIdentity {
  val category = DispatcherMetrics
}

object DispatcherMetrics extends MetricGroupCategory {
  val name = "dispatcher"

  case object MaximumPoolSize extends MetricIdentity { val name = "maximum-pool-size" }
  case object RunningThreadCount extends MetricIdentity { val name = "running-thread-count" }
  case object QueueTaskCount extends MetricIdentity { val name = "queued-task-count" }
  case object PoolSize extends MetricIdentity { val name = "pool-size" }

  case class DispatcherMetricRecorder(maximumPoolSize: Histogram, runningThreadCount: Histogram,
    queueTaskCount: Histogram, poolSize: Histogram)
      extends MetricGroupRecorder {

    def collect(context: CollectionContext): MetricGroupSnapshot =
      DispatcherMetricSnapshot(
        maximumPoolSize.collect(context),
        runningThreadCount.collect(context),
        queueTaskCount.collect(context),
        poolSize.collect(context))

    def cleanup: Unit = {}

  }

  case class DispatcherMetricSnapshot(maximumPoolSize: Histogram.Snapshot, runningThreadCount: Histogram.Snapshot,
      queueTaskCount: Histogram.Snapshot, poolSize: Histogram.Snapshot) extends MetricGroupSnapshot {

    type GroupSnapshotType = DispatcherMetricSnapshot

    def merge(that: DispatcherMetricSnapshot, context: CollectionContext): DispatcherMetricSnapshot =
      DispatcherMetricSnapshot(
        maximumPoolSize.merge(that.maximumPoolSize, context),
        runningThreadCount.merge(that.runningThreadCount, context),
        queueTaskCount.merge(that.queueTaskCount, context),
        poolSize.merge(that.poolSize, context))

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (MaximumPoolSize -> maximumPoolSize),
      (RunningThreadCount -> runningThreadCount),
      (QueueTaskCount -> queueTaskCount),
      (PoolSize -> poolSize))
  }

  val Factory = DispatcherMetricGroupFactory
}

case object DispatcherMetricGroupFactory extends MetricGroupFactory {

  import DispatcherMetrics._

  type GroupRecorder = DispatcherMetricRecorder

  def create(config: Config, system: ActorSystem): DispatcherMetricRecorder = {
    val settings = config.getConfig("precision.dispatcher")

    val maximumPoolSizeConfig = settings.getConfig("maximum-pool-size")
    val runningThreadCountConfig = settings.getConfig("running-thread-count")
    val queueTaskCountConfig = settings.getConfig("queued-task-count")
    val poolSizeConfig = settings.getConfig("pool-size")

    new DispatcherMetricRecorder(
      Histogram.fromConfig(maximumPoolSizeConfig),
      Histogram.fromConfig(runningThreadCountConfig),
      Histogram.fromConfig(queueTaskCountConfig),
      Histogram.fromConfig(poolSizeConfig))
  }

}
