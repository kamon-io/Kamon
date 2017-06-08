package kamon.metric

import kamon.util.MeasurementUnit
import kamon.util.MeasurementUnit.Dimension

class Scaler(targetTimeUnit: MeasurementUnit, targetInformationUnit: MeasurementUnit, dynamicRange: DynamicRange) {
  require(targetTimeUnit.dimension == Dimension.Time, "timeUnit must be in the time dimension.")
  require(targetInformationUnit.dimension == Dimension.Information, "informationUnit must be in the information dimension.")

  val scaleHistogram = new HdrHistogram("scaler", Map.empty, MeasurementUnit.none, dynamicRange)

  def scaleDistribution(metric: MetricDistribution): MetricDistribution = {
    metric.measurementUnit match {
      case MeasurementUnit(Dimension.Time, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricDistributionToTarget(metric, targetTimeUnit)

      case MeasurementUnit(Dimension.Information, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricDistributionToTarget(metric, targetInformationUnit)

      case _ => metric
    }
  }

  def scaleMetricValue(metric: MetricValue): MetricValue = {
    metric.measurementUnit match {
      case MeasurementUnit(Dimension.Time, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricValueToTarget(metric, targetTimeUnit)

      case MeasurementUnit(Dimension.Information, magnitude) if(magnitude != targetTimeUnit.magnitude) =>
        scaleMetricValueToTarget(metric, targetInformationUnit)

      case _ => metric
    }
  }

  private def scaleMetricDistributionToTarget(metric: MetricDistribution, targetUnit: MeasurementUnit): MetricDistribution = {
    metric.distribution.bucketsIterator.foreach(b => {
      val scaledValue = MeasurementUnit.scale(b.value, metric.measurementUnit, targetUnit)
      scaleHistogram.record(Math.ceil(scaledValue).toLong, b.frequency)
    })

    scaleHistogram.snapshot().copy(
      name = metric.name,
      tags = metric.tags,
      measurementUnit = targetUnit,
      dynamicRange = dynamicRange
    )
  }

  private def scaleMetricValueToTarget(metric: MetricValue, targetUnit: MeasurementUnit): MetricValue = {
    val scaledValue = MeasurementUnit.scale(metric.value, metric.measurementUnit, targetUnit)

    metric.copy(
      value = Math.ceil(scaledValue).toLong,
      measurementUnit = targetUnit
    )
  }
}
