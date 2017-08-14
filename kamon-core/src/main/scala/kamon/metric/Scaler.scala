/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import MeasurementUnit.Dimension

class Scaler(targetTimeUnit: MeasurementUnit, targetInformationUnit: MeasurementUnit, dynamicRange: DynamicRange) {
  require(targetTimeUnit.dimension == Dimension.Time, "timeUnit must be in the time dimension.")
  require(targetInformationUnit.dimension == Dimension.Information, "informationUnit must be in the information dimension.")

  val scaleHistogram = new AtomicHdrHistogram("scaler", Map.empty, MeasurementUnit.none, dynamicRange)

  def scaleDistribution(metric: MetricDistribution): MetricDistribution = {
    metric.unit match {
      case MeasurementUnit(Dimension.Time, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricDistributionToTarget(metric, targetTimeUnit)

      case MeasurementUnit(Dimension.Information, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricDistributionToTarget(metric, targetInformationUnit)

      case _ => metric
    }
  }

  def scaleMetricValue(metric: MetricValue): MetricValue = {
    metric.unit match {
      case MeasurementUnit(Dimension.Time, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricValueToTarget(metric, targetTimeUnit)

      case MeasurementUnit(Dimension.Information, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricValueToTarget(metric, targetInformationUnit)

      case _ => metric
    }
  }

  private def scaleMetricDistributionToTarget(metric: MetricDistribution, targetUnit: MeasurementUnit): MetricDistribution = {
    metric.distribution.bucketsIterator.foreach(b => {
      val scaledValue = MeasurementUnit.scale(b.value, metric.unit, targetUnit)
      scaleHistogram.record(Math.ceil(scaledValue).toLong, b.frequency)
    })

    scaleHistogram.snapshot(resetState = true).copy(
      name = metric.name,
      tags = metric.tags,
      unit = targetUnit,
      dynamicRange = dynamicRange
    )
  }

  private def scaleMetricValueToTarget(metric: MetricValue, targetUnit: MeasurementUnit): MetricValue = {
    val scaledValue = MeasurementUnit.scale(metric.value, metric.unit, targetUnit)

    metric.copy(
      value = Math.ceil(scaledValue).toLong,
      unit = targetUnit
    )
  }
}
