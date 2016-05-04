/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.influxdb

import java.lang.management.ManagementFactory

import com.typesafe.config.Config
import kamon.metric.instrument.Histogram
import kamon.metric.{ Entity, MetricKey }
import collection.JavaConversions._

trait TagsGenerator {
  protected val config: Config

  protected val application = config.getString("application-name")

  val hostname = {
    val hostnameOverride = config.getString("hostname-override")

    if (hostnameOverride.equals("none")) {
      ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
    } else {
      hostnameOverride
    }
  }

  protected val percentiles = config.getDoubleList("percentiles").toList

  protected def generateTags(entity: Entity, metricKey: MetricKey): Map[String, String] =
    entity.category match {
      case "trace-segment" ⇒
        Map(
          "category" -> normalize(entity.tags("trace")),
          "entity" -> normalize(entity.name),
          "hostname" -> normalize(hostname),
          "metric" -> normalize(metricKey.name))
      case _ ⇒
        Map(
          "category" -> normalize(entity.category),
          "entity" -> normalize(entity.name),
          "hostname" -> normalize(hostname),
          "metric" -> normalize(metricKey.name))
    }

  protected def histogramValues(hs: Histogram.Snapshot): Map[String, BigDecimal] = {
    val defaults = Map(
      "lower" -> BigDecimal(hs.min),
      "mean" -> average(hs),
      "upper" -> BigDecimal(hs.max))

    percentiles.foldLeft(defaults) { (acc, p) ⇒
      val fractional = p % 1
      val integral = (p - fractional).toInt

      val percentile = BigDecimal(hs.percentile(p))

      if (fractional > 0.0) acc ++ Map(s"p$p" -> percentile)
      else acc ++ Map(s"p$integral" -> percentile)
    }
  }

  protected def normalize(s: String): String =
    s
      .replace(": ", "-")
      .replace(":\\", "-")
      .replace(":", "-")
      .replace(" ", "-")
      .replace("\\", "-")
      .replace("/", "-")
      .replace(".", "-")

  private def average(histogram: Histogram.Snapshot): BigDecimal = {
    if (histogram.numberOfMeasurements == 0) BigDecimal(0.0)
    else BigDecimal(histogram.sum / histogram.numberOfMeasurements.toDouble)
  }

}

