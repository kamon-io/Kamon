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

package kamon
package metric


import kamon.util.MeasurementUnit
import scala.concurrent.duration.Duration

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
