package kamon.metric

import java.time.Duration

import kamon.metric.instrument._
import kamon.util.MeasurementUnit

trait EntityRecorder {
  def histogram(name: String): Histogram
  def histogram(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange): Histogram

  def minMaxCounter(name: String): MinMaxCounter
  def minMaxCounter(name: String, measurementUnit: MeasurementUnit, dynamicRange: DynamicRange, sampleFrequency: Duration): MinMaxCounter

  def gauge(name: String): Gauge

  def counter(name: String): Counter
}


class EntityRecorderImpl {

}