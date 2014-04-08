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

package kamon.statsd

import akka.actor.{ ActorSystem, Props, ActorRef, Actor }
import akka.io.{ Udp, IO }
import java.net.InetSocketAddress
import akka.util.ByteString
import kamon.Kamon
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.MetricSnapshot.Measurement
import kamon.metrics.InstrumentTypes.{ Counter, Gauge, Histogram, InstrumentType }

class StatsDMetricsSender extends Actor with UdpExtensionProvider {
  import context.system

  val statsDExtension = Kamon(StatsD)
  val remote = new InetSocketAddress(statsDExtension.hostname, statsDExtension.port)
  val metricKeyGenerator = new SimpleMetricKeyGenerator(context.system.settings.config)

  udpExtension ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case tick: TickMetricSnapshot ⇒ writeMetricsToRemote(tick, udpSender)
  }

  def writeMetricsToRemote(tick: TickMetricSnapshot, udpSender: ActorRef): Unit = {
    val dataBuilder = new MetricDataPacketBuilder(statsDExtension.maxPacketSize, udpSender, remote)

    for (
      (groupIdentity, groupSnapshot) ← tick.metrics;
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    ) {

      val key = ByteString(metricKeyGenerator.generateKey(groupIdentity, metricIdentity))

      for (measurement ← metricSnapshot.measurements) {
        val measurementData = encodeMeasurement(measurement, metricSnapshot.instrumentType)
        dataBuilder.appendMeasurement(key, measurementData)
      }
    }

    dataBuilder.flush()
  }

  def encodeMeasurement(measurement: Measurement, instrumentType: InstrumentType): ByteString = {
    def statsDMetricFormat(value: String, metricType: String, samplingRate: Double = 1D): ByteString =
      ByteString(value + "|" + metricType + (if (samplingRate != 1D) "|@" + samplingRate else ""))

    instrumentType match {
      case Histogram ⇒ statsDMetricFormat(measurement.value.toString, "ms", (1D / measurement.count))
      case Gauge     ⇒ statsDMetricFormat(measurement.value.toString, "g")
      case Counter   ⇒ ByteString.empty // TODO: Need to decide how to report counters, when we have them!
    }
  }
}

object StatsDMetricsSender {
  def props: Props = Props[StatsDMetricsSender]
}

trait UdpExtensionProvider {
  def udpExtension(implicit system: ActorSystem): ActorRef = IO(Udp)
}

class MetricDataPacketBuilder(maxPacketSize: Int, udpSender: ActorRef, remote: InetSocketAddress) {
  val metricSeparator = ByteString("\n")
  val measurementSeparator = ByteString(":")

  var lastKey = ByteString.empty
  var buffer = ByteString.empty

  def appendMeasurement(key: ByteString, measurementData: ByteString): Unit = {
    if (key == lastKey) {
      val dataWithoutKey = measurementSeparator ++ measurementData
      if (fitsOnBuffer(dataWithoutKey))
        buffer = buffer ++ dataWithoutKey
      else {
        flushToUDP(buffer)
        buffer = key ++ dataWithoutKey
      }
    } else {
      lastKey = key
      val dataWithoutSeparator = key ++ measurementSeparator ++ measurementData
      if (fitsOnBuffer(metricSeparator ++ dataWithoutSeparator)) {
        val mSeparator = if (buffer.length > 0) metricSeparator else ByteString.empty
        buffer = buffer ++ mSeparator ++ dataWithoutSeparator
      } else {
        flushToUDP(buffer)
        buffer = dataWithoutSeparator
      }
    }
  }

  def fitsOnBuffer(bs: ByteString): Boolean = (buffer.length + bs.length) <= maxPacketSize

  private def flushToUDP(bytes: ByteString): Unit = udpSender ! Udp.Send(bytes, remote)

  def flush(): Unit = {
    flushToUDP(buffer)
    buffer = ByteString.empty
  }
}