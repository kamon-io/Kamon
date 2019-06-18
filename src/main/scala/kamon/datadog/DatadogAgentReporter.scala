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
import java.text.{ DecimalFormat, DecimalFormatSymbols }
import java.util.Locale

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit.Dimension.{ Information, Time }
import kamon.metric.MeasurementUnit.{ information, time, Dimension }
import kamon.metric.{ MeasurementUnit, PeriodSnapshot }
import kamon.util.{ DynamicAccess, EnvironmentTags }
import kamon.Kamon
import kamon.module.MetricReporter
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

// 1 arg constructor is intended for injecting config via unit tests
class DatadogAgentReporter private[datadog] (c: DatadogAgentReporter.Configuration) extends MetricReporter {

  def this() = this(DatadogAgentReporter.readConfiguration(Kamon.config()))

  import DatadogAgentReporter._

  private val symbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  private val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)
  private val valueFormat = new DecimalFormat("#0.#########", symbols)

  override def start(): Unit =
    logger.info("Started the Kamon Datadog reporter")

  override def stop(): Unit = {}

  private[datadog] var config: Configuration = c

  override def reconfigure(config: Config): Unit = {
    this.config = readConfiguration(config)
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {

    for {
      counter <- snapshot.counters
      instrument <- counter.instruments
    } {
      config.packetBuffer.appendMeasurement(counter.name, config.measurementFormatter.formatMeasurement(encodeDatadogCounter(instrument.value, counter.settings.unit), instrument.tags))
    }

    for {
      gauge <- snapshot.gauges
      instrument <- gauge.instruments
    } {
      config.packetBuffer.appendMeasurement(gauge.name, config.measurementFormatter.formatMeasurement(encodeDatadogGauge(instrument.value, gauge.settings.unit), instrument.tags))
    }

    for {
      metric <- snapshot.histograms ++ snapshot.rangeSamplers ++ snapshot.timers
      instruments <- metric.instruments
      bucket <- instruments.value.bucketsIterator
    } {

      val bucketData = config.measurementFormatter.formatMeasurement(encodeDatadogHistogramBucket(bucket.value, bucket.frequency, metric.settings.unit), instruments.tags)
      config.packetBuffer.appendMeasurement(metric.name, bucketData)
    }

    config.packetBuffer.flush()

  }

  private def encodeDatadogHistogramBucket(value: Long, frequency: Long, unit: MeasurementUnit): String = {
    val metricType = if (unit.dimension == Dimension.Time) "ms" else "h"
    val samplingRate: Double = 1D / frequency.toDouble
    valueFormat.format(scale(value, unit)) + "|" + metricType + (if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else "")
  }

  private def encodeDatadogCounter(count: Long, unit: MeasurementUnit): String =
    valueFormat.format(scale(count, unit)) + "|c"

  private def encodeDatadogGauge(value: Double, unit: MeasurementUnit): String =
    valueFormat.format(scale(value, unit)) + "|g"

  private def scale(value: Double, unit: MeasurementUnit): Double = unit.dimension match {
    case Time if unit.magnitude != config.timeUnit.magnitude            => MeasurementUnit.convert(value, unit, config.timeUnit)
    case Information if unit.magnitude != information.bytes.magnitude => MeasurementUnit.convert(value, unit, information.bytes)
    case _                                                            => value.toDouble
  }

}

object DatadogAgentReporter {

  private val logger = LoggerFactory.getLogger(classOf[DatadogAgentReporter])

  trait MeasurementFormatter {
    def formatMeasurement(measurementData: String, tags: TagSet): String
  }

  private class DefaultMeasurementFormatter(config: Config) extends MeasurementFormatter {

    private val tagFilterKey = config.getString("filter-config-key")
    private val filter = Kamon.filter(tagFilterKey)
    private val envTags = EnvironmentTags.from(Kamon.environment, config.getConfig("additional-tags"))

    override def formatMeasurement(
      measurementData: String,
      tags:            TagSet
    ): String = {

      val filteredTags = envTags.iterator(_.toString) ++ tags.iterator(_.toString).filter(p => filter.accept(p.key))

      val stringTags: String = "|#" + filteredTags.map { p ⇒ s"${p.key}:${p.value}" }.mkString(",")

      StringBuilder.newBuilder
        .append(measurementData)
        .append(stringTags)
        .result()
    }
  }

  private[datadog] def readConfiguration(config: Config): Configuration = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val datadogConfig = config.getConfig("kamon.datadog")

    Configuration(
      timeUnit = readTimeUnit(datadogConfig.getString("time-unit")),
      informationUnit = readInformationUnit(datadogConfig.getString("information-unit")),
      measurementFormatter = getMeasurementFormatter(datadogConfig, dynamic),
      packetBuffer = getPacketBuffer(datadogConfig, dynamic)
    )
  }

  private def getMeasurementFormatter(config: Config, dynamic: DynamicAccess): MeasurementFormatter = {
    config.getString("agent.measurement-formatter") match {
      case "default" => new DefaultMeasurementFormatter(config)
      case fqn       => dynamic.createInstanceFor[MeasurementFormatter](fqn, List(classOf[Config] -> config)).get
    }
  }

  private def getPacketBuffer(config: Config, dynamic: DynamicAccess): PacketBuffer = {
    config.getString("agent.packetbuffer") match {
      case "default" => new PacketBufferImpl(config)
      case fqn       => dynamic.createInstanceFor[PacketBuffer](fqn, List(classOf[Config] -> config)).get
    }
  }

  private[datadog] case class Configuration(
    timeUnit:             MeasurementUnit,
    informationUnit:      MeasurementUnit,
    measurementFormatter: MeasurementFormatter,
    packetBuffer:         PacketBuffer
  )

  trait PacketBuffer {
    def appendMeasurement(key: String, measurementData: String): Unit
    def flush(): Unit

  }

  private class PacketBufferImpl(config: Config) extends PacketBuffer {
    val metricSeparator = "\n"
    val measurementSeparator = ":"
    var lastKey = ""
    var buffer = new StringBuilder()

    val maxPacketSizeInBytes = config.getBytes("agent.max-packet-size")
    val remote = new InetSocketAddress(config.getString("agent.hostname"), config.getInt("agent.port"))

    def appendMeasurement(key: String, measurementData: String): Unit = {
      val data = key + measurementSeparator + measurementData

      if (fitsOnBuffer(metricSeparator + data)) {
        val mSeparator = if (buffer.nonEmpty) metricSeparator else ""
        buffer.append(mSeparator).append(data)
      } else {
        flushToUDP(buffer.toString())
        buffer.clear()
        buffer.append(data)
      }
    }

    private def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

    private def flushToUDP(data: String): Unit = {
      val channel = DatagramChannel.open()
      try {
        channel.send(ByteBuffer.wrap(data.getBytes), remote)
      } finally {
        channel.close()
      }
    }

    def flush(): Unit = {
      flushToUDP(buffer.toString)
      buffer.clear()
    }
  }
}

