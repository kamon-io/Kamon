/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.statsd

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.{MeasurementUnit, _}
import kamon.module.{MetricReporter, ModuleFactory}
import kamon.statsd.StatsDReporter.MetricDataPacketBuffer
import kamon.util.DynamicAccess
import org.slf4j.LoggerFactory

class StatsDReporter(configPath: String) extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[StatsDReporter])
  @volatile private var reporterConfiguration =
    StatsDReporter.Settings.readSettings(Kamon.config().getConfig(configPath))

  val symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end loose precision if it needs to.
  val samplingRateFormat =
    new DecimalFormat("#.################################################################", symbols)
  val clientChannel: DatagramChannel = DatagramChannel.open()

  logger.info("Started the Kamon StatsD reporter")

  def this() = this("kamon.statsd")

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {
    reporterConfiguration = StatsDReporter.Settings.readSettings(config.getConfig(configPath))
    logger.info("The configuration was reloaded successfully.")
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val keyGenerator = reporterConfiguration.keyGenerator
    val packetBuffer = new MetricDataPacketBuffer(
      reporterConfiguration.maxPacketSize,
      reporterConfiguration.maxPacketsPerMilli,
      clientChannel,
      reporterConfiguration.agentAddress
    )

    for {
      counter <- snapshot.counters
      instrument <- counter.instruments
    } {

      if (instrument.value != 0 || reporterConfiguration.sendZeroValues)
        packetBuffer.appendMeasurement(
          key = keyGenerator.generateKey(counter.name, instrument.tags),
          measurementData = encodeStatsDCounter(reporterConfiguration, instrument.value, counter.settings.unit)
        )
    }

    for {
      gauge <- snapshot.gauges
      instrument <- gauge.instruments
    } {

      if (instrument.value != 0d || reporterConfiguration.sendZeroValues) {
        packetBuffer.appendMeasurement(
          key = keyGenerator.generateKey(gauge.name, instrument.tags),
          measurementData = encodeStatsDGauge(reporterConfiguration, instrument.value, gauge.settings.unit)
        )
      }
    }

    for {
      metric <- snapshot.histograms ++ snapshot.rangeSamplers ++ snapshot.timers
      instrument <- metric.instruments
      bucket <- instrument.value.bucketsIterator
    } {
      if (bucket.value != 0 || reporterConfiguration.sendZeroValues) {
        val bucketData = encodeStatsDTimer(reporterConfiguration, bucket.value, bucket.frequency, metric.settings.unit)
        packetBuffer.appendMeasurement(keyGenerator.generateKey(metric.name, instrument.tags), bucketData)
      }
    }

    packetBuffer.flush()
  }

  private def encodeStatsDCounter(config: StatsDReporter.Settings, count: Long, unit: MeasurementUnit): String =
    s"${scale(config, count, unit)}|c"

  private def encodeStatsDGauge(config: StatsDReporter.Settings, value: Double, unit: MeasurementUnit): String =
    s"${scale(config, value.toLong, unit)}|g"

  private def encodeStatsDTimer(
    config: StatsDReporter.Settings,
    level: Long,
    count: Long,
    unit: MeasurementUnit
  ): String = {
    val samplingRate: Double = 1d / count
    val sampled = if (samplingRate != 1d) "|@" + samplingRateFormat.format(samplingRate) else ""
    s"${scale(config, level, unit)}|ms$sampled"
  }

  private[statsd] def scale(config: StatsDReporter.Settings, value: Long, unit: MeasurementUnit): Double =
    unit.dimension match {
      case Time if unit.magnitude != config.timeUnit.magnitude => MeasurementUnit.convert(value, unit, config.timeUnit)
      case Information if unit.magnitude != config.informationUnit.magnitude =>
        MeasurementUnit.convert(value, unit, config.informationUnit)
      case _ => value
    }
}

object StatsDReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): StatsDReporter =
      new StatsDReporter()
  }

  case class Settings(
    agentAddress: InetSocketAddress,
    maxPacketSize: Long,
    timeUnit: MeasurementUnit,
    informationUnit: MeasurementUnit,
    keyGenerator: MetricKeyGenerator,
    maxPacketsPerMilli: Int,
    sendZeroValues: Boolean
  )

  object Settings {
    def readSettings(reporterConfiguration: Config): StatsDReporter.Settings = {
      StatsDReporter.Settings(
        agentAddress =
          new InetSocketAddress(reporterConfiguration.getString("hostname"), reporterConfiguration.getInt("port")),
        maxPacketSize = reporterConfiguration.getBytes("max-packet-size"),
        timeUnit = readTimeUnit(reporterConfiguration.getString("time-unit")),
        informationUnit = readInformationUnit(reporterConfiguration.getString("information-unit")),
        keyGenerator = loadKeyGenerator(reporterConfiguration.getString("metric-key-generator"), reporterConfiguration),
        maxPacketsPerMilli = reporterConfiguration.getInt("max-packets-per-milli"),
        sendZeroValues = reporterConfiguration.getBoolean("send-zero-values")
      )
    }

    private def loadKeyGenerator(keyGeneratorFQCN: String, config: Config): MetricKeyGenerator = {
      new DynamicAccess(getClass.getClassLoader).createInstanceFor[MetricKeyGenerator](
        keyGeneratorFQCN,
        (classOf[Config], config) :: Nil
      )
    }
  }

  private[statsd] class MetricDataPacketBuffer(
    maxPacketSizeInBytes: Long,
    maxPacketsPerMilli: Int,
    channel: DatagramChannel,
    remote: InetSocketAddress
  ) {

    val rateLimiter = new FlushRateLimiter(maxPacketsPerMilli)
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
          val mSeparator = if (buffer.nonEmpty) metricSeparator else ""
          buffer.append(mSeparator).append(dataWithoutSeparator)
        } else {
          flush()
          buffer.append(dataWithoutSeparator)
        }
      }
    }

    private def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

    def flush(): Unit = {
      rateLimiter.waitIfNecessary()

      flushToUDP(buffer.toString)
      buffer.clear()
    }

    private def flushToUDP(data: String): Unit = {
      channel.send(ByteBuffer.wrap(data.getBytes), remote)
    }
  }

  /**
    * A simple rate limiter that only allows for a said number of flush calls per millisecond. This class is not thread
    * safe and it is not meant to be used anywhere else outside of a reporter.
    */
  private[statsd] class FlushRateLimiter(maxPacketsPerMilli: Int) {
    private val startTime = System.nanoTime()
    private var lastMilli = currentMilli()
    private var countAtLastMilli = 0

    def waitIfNecessary(): Unit = {
      val current = currentMilli()

      if (current == lastMilli) {
        if (countAtLastMilli < maxPacketsPerMilli)
          countAtLastMilli += 1
        else {
          Thread.sleep(1)
          lastMilli = currentMilli()
          countAtLastMilli = 1
        }
      } else {
        lastMilli = current
        countAtLastMilli = 1
      }
    }

    private def currentMilli(): Long =
      (System.nanoTime() - startTime) / 1000000

  }
}
