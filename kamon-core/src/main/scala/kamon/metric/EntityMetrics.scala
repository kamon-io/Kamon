/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

import java.nio.{ LongBuffer }
import akka.actor.ActorSystem
import com.typesafe.config.Config

trait MetricGroupCategory {
  def name: String
}

trait MetricGroupIdentity {
  def name: String
  def category: MetricGroupCategory
}

trait MetricIdentity {
  def name: String
}

trait CollectionContext {
  def buffer: LongBuffer
}

object CollectionContext {
  def default: CollectionContext = new CollectionContext {
    val buffer: LongBuffer = LongBuffer.allocate(10000)
  }
}

trait MetricGroupRecorder {
  def collect(context: CollectionContext): MetricGroupSnapshot
  def cleanup: Unit
}

trait MetricSnapshot {
  type SnapshotType

  def merge(that: SnapshotType, context: CollectionContext): SnapshotType
}

trait MetricGroupSnapshot {
  type GroupSnapshotType

  def metrics: Map[MetricIdentity, MetricSnapshot]
  def merge(that: GroupSnapshotType, context: CollectionContext): GroupSnapshotType
}

private[kamon] trait MetricRecorder {
  type SnapshotType <: MetricSnapshot

  def collect(context: CollectionContext): SnapshotType
  def cleanup: Unit
}

trait MetricGroupFactory {
  type GroupRecorder <: MetricGroupRecorder
  def create(config: Config, system: ActorSystem): GroupRecorder
}

