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

package kamon.statsd

import akka.actor.Props
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }

/**
 * Factory for [[BatchStatsDMetricsSender]].
 * Use FQCN of the object in "kamon.statsd.statsd-metrics-sender"
 * to select [[BatchStatsDMetricsSender]] as your sender
 */
object BatchStatsDMetricsSender extends StatsDMetricsSenderFactory {
  override def props(statsDConfig: Config, metricKeyGenerator: MetricKeyGenerator): Props =
    Props(new BatchStatsDMetricsSender(statsDConfig, metricKeyGenerator))
}

/**
 * StatsD sender which sends a UDP packet every "kamon.statsd.flush-interval" or
 * as long as "kamon.statsd.batch-metric-sender.max-packet-size" is reached.
 * @param statsDConfig Config to read settings specific to this sender
 * @param metricKeyGenerator Key generator for all metrics sent by this sender
 */
class BatchStatsDMetricsSender(statsDConfig: Config, metricKeyGenerator: MetricKeyGenerator)
    extends UDPBasedStatsDMetricsSender(statsDConfig, metricKeyGenerator) {

  val maxPacketSizeInBytes = statsDConfig.getBytes("batch-metric-sender.max-packet-size")

  def writeMetricsToRemote(tick: TickMetricSnapshot, flushToUDP: String ⇒ Unit): Unit = {
    val packetBuilder = new MetricDataPacketBuilder(maxPacketSizeInBytes, flushToUDP)

    for (
      (entity, snapshot) ← tick.metrics;
      (metricKey, metricSnapshot) ← snapshot.metrics
    ) {

      val key = metricKeyGenerator.generateKey(entity, metricKey)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          hs.recordsIterator.foreach { record ⇒
            val scaled: Long = scaler.scale(metricKey.unitOfMeasurement, record.level)
            packetBuilder.appendMeasurement(key, encodeStatsDTimer(scaled, record.count))
          }

        case cs: Counter.Snapshot ⇒
          val scaled: Long = scaler.scale(metricKey.unitOfMeasurement, cs.count)
          packetBuilder.appendMeasurement(key, encodeStatsDCounter(scaled))
      }
    }

    packetBuilder.flush()
  }
}

class MetricDataPacketBuilder(maxPacketSizeInBytes: Long, flushToUDP: String ⇒ Unit) {
  val metricSeparator = "\n"
  val measurementSeparator = ":"

  var lastKey = ""
  var buffer = new StringBuilder()

  def appendMeasurement(key: String, measurementData: String): Unit = {
    if (key == lastKey) {
      val dataWithoutKey = measurementSeparator + measurementData
      if (fitsOnBuffer(dataWithoutKey))
        buffer.append(dataWithoutKey)
      else {
        flush()
        buffer.append(key).append(dataWithoutKey)
      }
    } else {
      lastKey = key
      val dataWithoutSeparator = key + measurementSeparator + measurementData
      if (fitsOnBuffer(metricSeparator + dataWithoutSeparator)) {
        val mSeparator = if (buffer.length > 0) metricSeparator else ""
        buffer.append(mSeparator).append(dataWithoutSeparator)
      } else {
        flush()
        buffer.append(dataWithoutSeparator)
      }
    }
  }

  def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

  def flush(): Unit = {
    flushToUDP(buffer.toString)
    buffer.clear()
  }
}