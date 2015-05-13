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
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import java.text.{ DecimalFormatSymbols, DecimalFormat }
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metric.{ SingleInstrumentEntityRecorder, MetricKey, Entity }
import java.util.Locale

class DatadogMetricsSender(remote: InetSocketAddress, maxPacketSizeInBytes: Long) extends Actor with UdpExtensionProvider {

  import context.system

  val appName = context.system.settings.config.getString("kamon.datadog.application-name")
  val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)

  udpExtension ! Udp.SimpleSender

  def receive = {
    case Udp.SimpleSenderReady ⇒
      context.become(ready(sender))
  }

  def ready(udpSender: ActorRef): Receive = {
    case tick: TickMetricSnapshot ⇒ writeMetricsToRemote(tick, udpSender)
  }

  def writeMetricsToRemote(tick: TickMetricSnapshot, udpSender: ActorRef): Unit = {
    val packetBuilder = new MetricDataPacketBuilder(maxPacketSizeInBytes, udpSender, remote)

    for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } {

      val key = buildMetricName(groupIdentity, metricIdentity)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
          hs.recordsIterator.foreach { record ⇒
            val measurementData = formatMeasurement(groupIdentity, metricIdentity, encodeDatadogTimer(record.level, record.count))
            packetBuilder.appendMeasurement(key, measurementData)

          }

        case cs: Counter.Snapshot ⇒
          if (cs.count > 0) {
            val measurementData = formatMeasurement(groupIdentity, metricIdentity, encodeDatadogCounter(cs.count))
            packetBuilder.appendMeasurement(key, measurementData)
          }
      }
    }
    packetBuilder.flush()
  }

  def formatMeasurement(entity: Entity, metricKey: MetricKey, measurementData: String): String =
    StringBuilder.newBuilder
      .append(measurementData)
      .append(buildIdentificationTag(entity, metricKey))
      .result()

  def encodeDatadogTimer(level: Long, count: Long): String = {
    val samplingRate: Double = 1D / count
    level.toString + "|ms" + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")
  }

  def encodeDatadogCounter(count: Long): String = count.toString + "|c"

  def isSingleInstrumentEntity(entity: Entity): Boolean =
    SingleInstrumentEntityRecorder.AllCategories.contains(entity.category)

  def buildMetricName(entity: Entity, metricKey: MetricKey): String =
    if (isSingleInstrumentEntity(entity))
      s"$appName.${entity.category}.${entity.name}"
    else
      s"$appName.${entity.category}.${metricKey.name}"

  def buildIdentificationTag(entity: Entity, metricKey: MetricKey): String = {
    def tagsString: String = entity.tags.map { case (k, v) ⇒ k + ":" + v } mkString ","

    if (isSingleInstrumentEntity(entity)) {
      if (entity.tags.nonEmpty) "|#" + tagsString else ""

    } else {
      val normalizedEntityName = entity.name.replace(" ", "")

      if (entity.tags.nonEmpty) {
        s"|#${entity.category}:${normalizedEntityName},$tagsString"
      } else
        s"|#${entity.category}:${normalizedEntityName}"
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
    val data = key + measurementSeparator + measurementData

    if (fitsOnBuffer(metricSeparator + data)) {
      val mSeparator = if (buffer.length > 0) metricSeparator else ""
      buffer.append(mSeparator).append(data)
    } else {
      flushToUDP(buffer.toString())
      buffer.clear()
      buffer.append(data)
    }
  }

  def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

  private def flushToUDP(data: String): Unit = udpSender ! Udp.Send(ByteString(data), remote)

  def flush(): Unit = {
    flushToUDP(buffer.toString)
    buffer.clear()
  }
}
