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

import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }

trait TimestampGenerator {
  def timestamp = System.currentTimeMillis()
}

case class BatchInfluxDBMetricsPacker(config: Config) extends InfluxDBMetricsPacker(config) with TagsGenerator with TimestampGenerator {
  protected val maxPacketSizeInBytes = config.getBytes("max-packet-size")

  protected def generateMetricsData(tick: TickMetricSnapshot, flushToDestination: String ⇒ Any): Unit = {
    val packetBuilder = new MetricDataPacketBuilder(maxPacketSizeInBytes, flushToDestination)

    for {
      (entity, snapshot) ← tick.metrics
      (metricKey, metricSnapshot) ← snapshot.metrics
    } {
      val tags = generateTags(entity, metricKey)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          if (!hs.isEmpty) packetBuilder.appendMeasurement(s"$application-timers", tags, histogramValues(hs), timestamp * 1000000)
        case cs: Counter.Snapshot ⇒
          packetBuilder.appendMeasurement(s"$application-counters", tags, Map("value" -> BigDecimal(cs.count)), timestamp * 1000000)
      }
    }

    packetBuilder.flush()
  }
}
