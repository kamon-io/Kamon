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

case class MetricGroupIdentity(name: String, category: MetricGroupIdentity.Category)

trait MetricIdentity {
  def name: String
}

trait MetricGroupRecorder {
  def record(identity: MetricIdentity, value: Long)
  def collect: MetricGroupSnapshot
}

trait MetricGroupSnapshot {
  def metrics: Map[MetricIdentity, MetricSnapshot]
}

trait MetricRecorder {
  def record(value: Long)
  def collect(): MetricSnapshot
}

trait MetricSnapshot {
  def numberOfMeasurements: Long
  def measurementLevels: Vector[Measurement]

  def max: Long = measurementLevels.lastOption.map(_.value).getOrElse(0)
  def min: Long = measurementLevels.headOption.map(_.value).getOrElse(0)

  def merge(that: MetricSnapshot): MetricSnapshot = {
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

    val totalNrOfMeasurements = go(measurementLevels, that.measurementLevels, 0)
    DefaultMetricSnapshot(totalNrOfMeasurements, mergedMeasurements.result())
  }
}

object MetricSnapshot {
  case class Measurement(value: Long, count: Long) {
    def merge(that: Measurement) = Measurement(value, count + that.count)
  }
}

case class DefaultMetricSnapshot(numberOfMeasurements: Long, measurementLevels: Vector[MetricSnapshot.Measurement]) extends MetricSnapshot

object MetricGroupIdentity {
  trait Category {
    def entityName: String
  }

  val AnyCategory = new Category {
    val entityName: String = "match-all"
    override def equals(that: Any): Boolean = that.isInstanceOf[Category]
  }
}

trait MetricGroupFactory {
  type GroupRecorder <: MetricGroupRecorder
  def create(config: Config): GroupRecorder
}

