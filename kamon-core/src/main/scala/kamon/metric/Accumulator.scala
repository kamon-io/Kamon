package kamon.metric

import kamon.util.MeasurementUnit


class DistributionAccumulator(dynamicRange: DynamicRange) {
  private val accumulatorHistogram = new HdrHistogram("metric-distribution-accumulator",
    tags = Map.empty, measurementUnit = MeasurementUnit.none, dynamicRange)


  def add(distribution: Distribution): Unit = {
    distribution.bucketsIterator.foreach(b => accumulatorHistogram.record(b.value, b.frequency))
  }

  def result(): Distribution =
    accumulatorHistogram.snapshot().distribution
}
