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

package kamon.metrics

import annotation.tailrec
import com.typesafe.config.Config
import kamon.metrics.MetricSnapshot.Measurement

trait MetricGroupCategory {
  def name: String
}

trait MetricGroupIdentity {
  def name: String
  def category: MetricGroupCategory
}

trait MetricIdentity {
  def name: String
  def tag: String
}

trait MetricGroupRecorder {
  def collect: MetricGroupSnapshot
}

trait MetricGroupSnapshot {
  def metrics: Map[MetricIdentity, MetricSnapshotLike]
}

case class DefaultMetricGroupSnapshot(metrics: Map[MetricIdentity, MetricSnapshotLike]) extends MetricGroupSnapshot

trait MetricRecorder {
  def record(value: Long)
  def collect(): MetricSnapshotLike
}

trait MetricSnapshotLike {
  def numberOfMeasurements: Long
  def scale: Scale
  def measurements: Vector[Measurement]

  def max: Long = measurements.lastOption.map(_.value).getOrElse(0)
  def min: Long = measurements.headOption.map(_.value).getOrElse(0)

  def merge(that: MetricSnapshotLike): MetricSnapshotLike = {
    val mergedMeasurements = Vector.newBuilder[Measurement]

    @tailrec def go(left: Vector[Measurement], right: Vector[Measurement], totalNrOfMeasurements: Long): Long = {
      if (left.nonEmpty && right.nonEmpty) {
        val leftValue = left.head
        val rightValue = right.head

        if (rightValue.value == leftValue.value) {
          val merged = rightValue.merge(leftValue)
          mergedMeasurements += merged
          go(left.tail, right.tail, totalNrOfMeasurements + merged.count)
        } else {
          if (leftValue.value < rightValue.value) {
            mergedMeasurements += leftValue
            go(left.tail, right, totalNrOfMeasurements + leftValue.count)
          } else {
            mergedMeasurements += rightValue
            go(left, right.tail, totalNrOfMeasurements + rightValue.count)
          }
        }
      } else {
        if (left.isEmpty && right.nonEmpty) {
          mergedMeasurements += right.head
          go(left, right.tail, totalNrOfMeasurements + right.head.count)
        } else {
          if (left.nonEmpty && right.isEmpty) {
            mergedMeasurements += left.head
            go(left.tail, right, totalNrOfMeasurements + left.head.count)
          } else totalNrOfMeasurements
        }
      }
    }

    val totalNrOfMeasurements = go(measurements, that.measurements, 0)
    MetricSnapshot(totalNrOfMeasurements, scale, mergedMeasurements.result())
  }
}

case class MetricSnapshot(numberOfMeasurements: Long, scale: Scale, measurements: Vector[MetricSnapshot.Measurement]) extends MetricSnapshotLike

object MetricSnapshot {
  case class Measurement(value: Long, count: Long) {
    def merge(that: Measurement) = Measurement(value, count + that.count)
  }
}

trait MetricGroupFactory {
  type GroupRecorder <: MetricGroupRecorder
  def create(config: Config): GroupRecorder
}

