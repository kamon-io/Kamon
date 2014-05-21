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
import kamon.metrics.{ MetricIdentity, MetricGroupIdentity }

class DatadogMetricsSender(remote: InetSocketAddress) extends Actor with UdpExtensionProvider {
  import context.system

  val appName = context.system.settings.config.getString("kamon.datadog.application-name")
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
    def flushToUDP(data: String): Unit = udpSender ! Udp.Send(ByteString(data), remote)

    for (
      (groupIdentity, groupSnapshot) ← tick.metrics;
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    ) {

      for (measurement ← metricSnapshot.measurements) {
        val measurementData = formatMeasurement(groupIdentity, metricIdentity, measurement, metricSnapshot.instrumentType)
        flushToUDP(measurementData)
      }
    }
  }

  def formatMeasurement(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity, measurement: Measurement,
                        instrumentType: InstrumentType): String = {

    StringBuilder.newBuilder
      .append(buildMetricName(groupIdentity, metricIdentity))
      .append(":")
      .append(buildMeasurementData(measurement, instrumentType))
      .append(buildIdentificationTag(groupIdentity, metricIdentity))
      .result()
  }

  def buildMeasurementData(measurement: Measurement, instrumentType: InstrumentType): String = {
    def dataDogDMetricFormat(value: String, metricType: String, samplingRate: Double = 1D): String =
      value + "|" + metricType + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")

    instrumentType match {
      case Histogram ⇒ dataDogDMetricFormat(measurement.value.toString, "ms", (1D / measurement.count))
      case Gauge     ⇒ dataDogDMetricFormat(measurement.value.toString, "g")
      case Counter   ⇒ dataDogDMetricFormat(measurement.count.toString, "c")
    }
  }

  def buildMetricName(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String =
    appName + "." + groupIdentity.category.name + "." + metricIdentity.name

  def buildIdentificationTag(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String =
    "|#" + groupIdentity.category.name + ":" + groupIdentity.name
}

object DatadogMetricsSender {
  def props(remote: InetSocketAddress): Props = Props(new DatadogMetricsSender(remote))
}

trait UdpExtensionProvider {
  def udpExtension(implicit system: ActorSystem): ActorRef = IO(Udp)
}
