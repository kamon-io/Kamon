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

package kamon.datadog

import akka.actor.{ ActorSystem, Props, ActorRef, Actor }
import akka.io.{ Udp, IO }
import java.net.InetSocketAddress
import akka.util.ByteString
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.MetricSnapshot.Measurement
import kamon.metrics.InstrumentTypes.{ Counter, Gauge, Histogram, InstrumentType }
import java.text.DecimalFormat

class DatadogMetricsSender(remote: InetSocketAddress, maxPacketSizeInBytes: Long) extends Actor with UdpExtensionProvider {
  import context.system

  val metricKeyGenerator = new SimpleMetricKeyGenerator(context.system.settings.config)
  val samplingRateFormat = new DecimalFormat()
  samplingRateFormat.setMaximumFractionDigits(128) // Absurdly high, let the other end loss precision if it needs to.

  udpExtension ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case tick: TickMetricSnapshot ⇒ writeMetricsToRemote(tick, udpSender)
  }

  def writeMetricsToRemote(tick: TickMetricSnapshot, udpSender: ActorRef): Unit = {
    val dataBuilder = new MetricDataPacketBuilder(maxPacketSizeInBytes, udpSender, remote)

    for (
      (groupIdentity, groupSnapshot) ← tick.metrics;
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    ) {

      val key = metricKeyGenerator.generateKey(groupIdentity, metricIdentity)

      for (measurement ← metricSnapshot.measurements) {
        val measurementData = encodeMeasurement(measurement, metricSnapshot.instrumentType)
        dataBuilder.appendMeasurement(key, measurementData)
      }
    }

    dataBuilder.flush()
  }

  def encodeMeasurement(measurement: Measurement, instrumentType: InstrumentType): String = {
    def statsDMetricFormat(value: String, metricType: String, samplingRate: Double = 1D): String =
      value + "|" + metricType + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")

    instrumentType match {
      case Histogram ⇒ statsDMetricFormat(measurement.value.toString, "ms", (1D / measurement.count))
      case Gauge     ⇒ statsDMetricFormat(measurement.value.toString, "g")
      case Counter   ⇒ "" // TODO: Need to decide how to report counters, when we have them!
    }
  }
}

object DatadogMetricsSender {
  def props(remote: InetSocketAddress, maxPacketSize: Long): Props = Props(new DatadogMetricsSender(remote, maxPacketSize))
}

trait UdpExtensionProvider {
  def udpExtension(implicit system: ActorSystem): ActorRef = IO(Udp)
}

class MetricDataPacketBuilder(maxPacketSizeInBytes: Long, udpSender: ActorRef, remote: InetSocketAddress) {
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
        flushToUDP(buffer.toString())
        buffer.clear()
        buffer.append(key).append(dataWithoutKey)
      }
    } else {
      lastKey = key
      val dataWithoutSeparator = key + measurementSeparator + measurementData
      if (fitsOnBuffer(metricSeparator + dataWithoutSeparator)) {
        val mSeparator = if (buffer.length > 0) metricSeparator else ""
        buffer.append(mSeparator).append(dataWithoutSeparator)
      } else {
        flushToUDP(buffer.toString())
        buffer.clear()
        buffer.append(dataWithoutSeparator)
      }
    }
  }

  def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

  private def flushToUDP(data: String): Unit = udpSender ! Udp.Send(ByteString(data), remote)

  def flush(): Unit = {
    flushToUDP(buffer.toString)
    buffer.clear()
  }
}
