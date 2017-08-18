/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit.Dimension.{Information, Time}
import kamon.metric.MeasurementUnit.{information, time}
import kamon.metric.{MeasurementUnit, _}
import kamon.util.DynamicAccess
import kamon.{Kamon, MetricReporter}
import org.slf4j.LoggerFactory


class StatsDReporter extends MetricReporter  {
  private val logger = LoggerFactory.getLogger(classOf[StatsDReporter])

  private val configuration:AtomicReference[Configuration] = new AtomicReference[Configuration]()

  val symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)
  symbols.setDecimalSeparator('.') // Just in case there is some weird locale config we are not aware of.

  // Absurdly high number of decimal digits, let the other end lose precision if it needs to.
  val samplingRateFormat = new DecimalFormat("#.################################################################", symbols)


  override def start(): Unit = {
    logger.info("Started the Kamon StatsD reporter")
    configuration.set(readConfiguration(Kamon.config()))
  }

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {
    val current = configuration.get()
    if(configuration.compareAndSet(current, readConfiguration(config)))
      logger.info("The configuration was reloaded successfully.")
    else
      logger.warn("Unable to reload configuration.")
  }

  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = {
    val config = configuration.get()
    val keyGenerator = config.keyGenerator
    val clientChannel = DatagramChannel.open()

    val packetBuffer = new MetricDataPacketBuffer(config.maxPacketSize, clientChannel, config.agentAddress)

    for (counter <- snapshot.metrics.counters) {
      packetBuffer.appendMeasurement(keyGenerator.generateKey(counter.name, counter.tags), encodeStatsDCounter(counter.value, counter.unit))
    }

    for (gauge <- snapshot.metrics.gauges) {
      packetBuffer.appendMeasurement(keyGenerator.generateKey(gauge.name, gauge.tags), encodeStatsDGauge(gauge.value, gauge.unit))
    }

    for (metric <- snapshot.metrics.histograms ++ snapshot.metrics.minMaxCounters;
         bucket <- metric.distribution.bucketsIterator) {

      val bucketData = encodeStatsDTimer(bucket.value, bucket.frequency, metric.unit)
      packetBuffer.appendMeasurement(keyGenerator.generateKey(metric.name, metric.tags), bucketData)
    }

    packetBuffer.flush()
  }


  private def encodeStatsDTimer(level: Long, count: Long, unit: MeasurementUnit): String = {
    val samplingRate: Double = 1D / count
    s"${scale(level, unit)}|ms${if (samplingRate != 1D) "|@" + samplingRateFormat.format(samplingRate) else ""}"
  }

  private def encodeStatsDCounter(count: Long, unit: MeasurementUnit): String = s"${scale(count, unit)}|c"

  private def encodeStatsDGauge(value:Long, unit: MeasurementUnit): String = s"${scale(value, unit)}|g"

  private def scale(value: Long, unit: MeasurementUnit): Double = unit.dimension match {
    case Time         if unit.magnitude != time.seconds       => MeasurementUnit.scale(value, unit, time.seconds)
    case Information  if unit.magnitude != information.bytes  => MeasurementUnit.scale(value, unit, information.bytes)
    case _ => value
  }

  private def readConfiguration(config: Config): Configuration = {
    val statsDConfig = config.getConfig("kamon.statsd")
    val agentAddress = new InetSocketAddress(statsDConfig.getString("hostname"), statsDConfig.getInt("port"))
    val maxPacketSize = statsDConfig.getBytes("max-packet-size")
    val timeUnit = readTimeUnit(statsDConfig.getString("time-unit"))
    val informationUnit = readInformationUnit(statsDConfig.getString("information-unit"))
    val keyGenerator = loadKeyGenerator(statsDConfig.getString("metric-key-generator"), config)

    Configuration(agentAddress, maxPacketSize, timeUnit, informationUnit, keyGenerator)
  }

  private def loadKeyGenerator(keyGeneratorFQCN: String, config:Config): MetricKeyGenerator = {
    new DynamicAccess(getClass.getClassLoader).createInstanceFor[MetricKeyGenerator](keyGeneratorFQCN, (classOf[Config], config) :: Nil).get
  }


  private class MetricDataPacketBuffer(maxPacketSizeInBytes: Long, channel: DatagramChannel, remote: InetSocketAddress) {
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

    def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

    private def flushToUDP(data: String): Unit =  {
      channel.send(ByteBuffer.wrap(data.getBytes), remote)
    }

    def flush(): Unit = {
      flushToUDP(buffer.toString)
      buffer.clear()
    }
  }

  private case class Configuration(agentAddress: InetSocketAddress,
                                   maxPacketSize: Long,
                                   timeUnit: MeasurementUnit,
                                   informationUnit: MeasurementUnit,
                                   keyGenerator: MetricKeyGenerator)
}