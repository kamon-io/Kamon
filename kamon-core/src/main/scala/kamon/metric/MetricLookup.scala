package kamon
package metric

import java.time.Duration

import kamon.metric.instrument._
import kamon.util.MeasurementUnit

trait MetricLookup {

  def histogram(name: String): Histogram =
    histogram(name, MeasurementUnit.none, Map.empty[String, String], None)

  def histogram(name: String, unit: MeasurementUnit): Histogram =
    histogram(name, unit, Map.empty[String, String], None)

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String]): Histogram =
    histogram(name, unit, tags, None)

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: DynamicRange): Histogram =
    histogram(name, unit, tags, Some(dynamicRange))

  def counter(name: String): Counter =
    counter(name, MeasurementUnit.none, Map.empty[String, String])

  def counter(name: String, unit: MeasurementUnit): Counter =
    counter(name, unit, Map.empty[String, String])

  def gauge(name: String): Gauge =
    gauge(name, MeasurementUnit.none, Map.empty[String, String])

  def gauge(name: String, unit: MeasurementUnit): Gauge =
    gauge(name, unit, Map.empty[String, String])

  def minMaxCounter(name: String): MinMaxCounter =
    minMaxCounter(name, MeasurementUnit.none, Map.empty[String, String], None, None)

  def minMaxCounter(name: String, unit: MeasurementUnit): MinMaxCounter =
    minMaxCounter(name, unit, Map.empty[String, String], None, None)

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String]): MinMaxCounter =
    minMaxCounter(name, unit, tags, None, None)

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], sampleInterval: Duration): MinMaxCounter =
    minMaxCounter(name, unit, tags, Option(sampleInterval), None)

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], sampleInterval: Duration,
      dynamicRange: DynamicRange): MinMaxCounter =
    minMaxCounter(name, unit, tags, Option(sampleInterval), Option(dynamicRange))

  def histogram(name: String, unit: MeasurementUnit, tags: Map[String, String], dynamicRange: Option[DynamicRange]): Histogram

  def counter(name: String, unit: MeasurementUnit, tags: Map[String, String]): Counter

  def gauge(name: String, unit: MeasurementUnit, tags: Map[String, String]): Gauge

  def minMaxCounter(name: String, unit: MeasurementUnit, tags: Map[String, String], sampleInterval: Option[Duration],
    dynamicRange: Option[DynamicRange]): MinMaxCounter
}
