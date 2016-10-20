/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.jmx.extension

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import kamon.metric.{
  GenericEntityRecorder,
  MetricsModule,
  EntityRecorderFactory
}
import kamon.metric.instrument._
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.metric.instrument.Gauge.CurrentValueCollector

case object MetricDefinition {

  // enum of different types of kamon metrics
  object MetricTypeEnum extends Enumeration {
    type MetricType = Value
    val COUNTER, HISTOGRAM, MIN_MAX_COUNTER, GAUGE = Value
  }

  import MetricTypeEnum._

  def toMetricType(tpe: String): MetricType = tpe.toLowerCase match {
    case "counter"         ⇒ MetricTypeEnum.COUNTER
    case "histogram"       ⇒ MetricTypeEnum.HISTOGRAM
    case "min_max_counter" ⇒ MetricTypeEnum.MIN_MAX_COUNTER
    case "gauge"           ⇒ MetricTypeEnum.GAUGE
    case _                 ⇒ throw new Exception("unknown metric type " + tpe)
  }

  // gets a kamon dynamic range from a config
  def getDynamicRange(metricConfig: Map[String, Any]): Option[DynamicRange] =
    if (metricConfig.contains("lowest") && metricConfig.contains("highest") &&
      metricConfig.contains("precision")) {
      Some(DynamicRange(
        metricConfig("lowest").asInstanceOf[Long],
        metricConfig("highest").asInstanceOf[Long],
        metricConfig("precision").asInstanceOf[Int]))
    } else {
      None
    }

  // gets a unit of measure from a config
  def getUnitOfMeasure(metricConfig: Map[String, Any]): UnitOfMeasurement =
    if (metricConfig.contains("unit")) {
      metricConfig("unit").asInstanceOf[String] match {
        // memory based
        case "b"  ⇒ Memory.Bytes
        case "Kb" ⇒ Memory.KiloBytes
        case "Mb" ⇒ Memory.MegaBytes
        case "Gb" ⇒ Memory.GigaBytes
        // time based
        case "n"  ⇒ Time.Nanoseconds
        case "µs" ⇒ Time.Microseconds
        case "ms" ⇒ Time.Milliseconds
        case "s"  ⇒ Time.Seconds
      }
    } else {
      UnitOfMeasurement.Unknown
    }

  // gets a duration from a config
  def getFiniteDuration(
    metricConfig: Map[String, Any]): Option[FiniteDuration] =

    if (metricConfig.contains("interval")) {
      val millis: Long = metricConfig("interval").asInstanceOf[Long]
      Some(new FiniteDuration(millis, TimeUnit.MILLISECONDS))
    } else {
      None
    }
}

// scala sillyness
import kamon.jmx.extension.MetricDefinition._

/**
 * This call represents one kamon metric which can be a counter, gauge,
 * histogram or min-max counter.  It allows a metric to be represented
 * in a config file.
 */
case class MetricDefinition(
    metricType: MetricTypeEnum.MetricType, name: String,
    unitOfMeasure: UnitOfMeasurement, // = UnitOfMeasurement.Unknown,
    range: Option[DynamicRange] = None,
    refreshInterval: Option[FiniteDuration] = None,
    valueCollector: Option[CurrentValueCollector] = None) {

  /**
   * constructor that works from the values extracted from a config file
   * @param metricConfig the metric's representation properties extracted
   * from a config object
   * @param name the name of this metric
   * @param valueCollector if this metric is a gauge, then this class is used
   * to get values from the instrumentation class (ie mbean)
   */
  def this(
    metricConfig: Map[String, Any], name: Option[String],
    valueCollector: Option[CurrentValueCollector]) =
    this(
      toMetricType(metricConfig("type").asInstanceOf[String]),
      name.getOrElse(metricConfig("name").asInstanceOf[String]),
      getUnitOfMeasure(metricConfig),
      getDynamicRange(metricConfig),
      getFiniteDuration(metricConfig),
      valueCollector)
}
