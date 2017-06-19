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

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

import com.typesafe.config.Config
import kamon.{Kamon, MetricReporter}
import kamon.metric._
import kamon.util.MeasurementUnit
import kamon.util.MeasurementUnit.Dimension.{Information, Time}
import kamon.util.MeasurementUnit.{Dimension, information, time}
import org.slf4j.LoggerFactory


class DatadogAgentReporter extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[DatadogAgentReporter])
  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  private val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)
  private val valueFormat = new DecimalFormat("#0.#########", symbols)

  override def start(): Unit =
    logger.info("Started the Kamon Datadog reporter")

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {}


  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = {
    val config = readConfiguration(Kamon.config())
    val clientChannel = DatagramChannel.open()
    val packetBuffer = new PacketBuffer(config.maxPacketSize, clientChannel, config.agentAddress)
    val serviceNameTag = "|#service:" + Kamon.environment.service


    for(counter <- snapshot.metrics.counters) {
      packetBuffer.appendMeasurement(counter.name, formatMeasurement(encodeDatadogCounter(counter.value, counter.unit), serviceNameTag, counter.tags))
    }

    for(gauge <- snapshot.metrics.gauges) {
      packetBuffer.appendMeasurement(gauge.name, formatMeasurement(encodeDatadogGauge(gauge.value, gauge.unit), serviceNameTag, gauge.tags))
    }

    for(metric <- snapshot.metrics.histograms ++ snapshot.metrics.minMaxCounters;
        bucket <- metric.distribution.bucketsIterator) {

      val bucketData = formatMeasurement(encodeDatadogHistogramBucket(bucket.value, bucket.frequency, metric.unit), serviceNameTag, metric.tags)
      packetBuffer.appendMeasurement(metric.name, bucketData)
    }

    packetBuffer.flush()

  }

  private def formatMeasurement(measurementData: String, baseTag: String, tags: Map[String, String]): String = {
    val stringTags: String = if(tags.isEmpty) baseTag else baseTag + "," + (tags.map { case (k, v) ⇒ k + ":" + v } mkString ",")

    StringBuilder.newBuilder
    .append(measurementData)
    .append(stringTags)
    .result()
  }

  private def encodeDatadogHistogramBucket(value: Long, frequency: Long, unit: MeasurementUnit): String = {
    val metricType = if(unit.dimension == Dimension.Time) "ms" else "h"
    val samplingRate: Double = 1D / frequency.toDouble
    valueFormat.format(scale(value, unit)) + "|" + metricType + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")
  }

  private def encodeDatadogCounter(count: Long, unit: MeasurementUnit): String =
    valueFormat.format(scale(count, unit)) + "|c"

  private def encodeDatadogGauge(value: Long, unit: MeasurementUnit): String =
    valueFormat.format(scale(value, unit)) + "|g"

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds       => MeasurementUnit.scale(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes  => MeasurementUnit.scale(value, unit, information.bytes)
    case _ => value
  }


  private def readConfiguration(config: Config): Configuration = {
    val datadogConfig = config.getConfig("kamon.datadog")

    Configuration(
      agentAddress = new InetSocketAddress(datadogConfig.getString("agent.hostname"), datadogConfig.getInt("agent.port")),
      maxPacketSize = datadogConfig.getBytes("agent.max-packet-size"),
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit"))
    )
  }

  private case class Configuration(agentAddress: InetSocketAddress, maxPacketSize: Long, timeUnit: MeasurementUnit,
    informationUnit: MeasurementUnit)


  private class PacketBuffer(maxPacketSizeInBytes: Long, channel: DatagramChannel, remote: InetSocketAddress) {
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

    private def flushToUDP(data: String): Unit =  {
      println(data)
      channel.send(ByteBuffer.wrap(data.getBytes), remote)
    }

    def flush(): Unit = {
      flushToUDP(buffer.toString)
      buffer.clear()
    }
  }
}

