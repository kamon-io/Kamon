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

package kamon.graphite

import java.io.{BufferedOutputStream, OutputStream}
import java.net.Socket
import java.nio.charset.{Charset, StandardCharsets}

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, ModuleFactory}
import kamon.tag.{Tag, TagSet}
import kamon.util.{EnvironmentTags, Filter}
import org.slf4j.{Logger, LoggerFactory}

class GraphiteReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): GraphiteReporter = new GraphiteReporter()
}

class GraphiteReporter extends MetricReporter {
  private val log = LoggerFactory.getLogger(classOf[GraphiteReporter])
  private var reporterConfig = GraphiteSenderConfig(Kamon.config())
  log.info("starting Graphite reporter {}", reporterConfig)
  private var sender: GraphiteSender = buildNewSender()

  override def stop(): Unit = {
    sender.close()
  }

  override def reconfigure(config: Config): Unit = {
    reporterConfig = GraphiteSenderConfig(config)
    log.info("restarting new Graphite reporter {}", reporterConfig)
    sender = buildNewSender()
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    try {
      sender.reportPeriodSnapshot(snapshot)
    } catch {
      case e: Throwable =>
        log.warn(
          s"sending failed to ${reporterConfig.hostname}:${reporterConfig.port} - dispose current snapshot and retry sending next snapshot using a new connection",
          e
        )
        sender.close()
        sender = buildNewSender()
    }
  }

  private def buildNewSender(): GraphiteSender = new GraphiteSender(reporterConfig) with TcpSender
}

private case class GraphiteSenderConfig(
  hostname: String,
  port: Int,
  metricPrefix: String,
  legacySupport: Boolean,
  envTags: TagSet,
  tagFilter: Filter,
  percentiles: Seq[Double]
)
private object GraphiteSenderConfig {
  def apply(config: Config): GraphiteSenderConfig = {
    val graphiteConfig = config.getConfig("kamon.graphite")
    val hostname = graphiteConfig.getString("hostname")
    val port = graphiteConfig.getInt("port")
    val metricPrefix = graphiteConfig.getString("metric-name-prefix")
    val legacySupport = graphiteConfig.getBoolean("legacy-support")
    val envTags = EnvironmentTags.from(Kamon.environment, graphiteConfig.getConfig("environment-tags"))
    val tagFilter = Kamon.filter("kamon.graphite.tag-filter")
    import scala.collection.JavaConverters._
    val percentiles = graphiteConfig.getDoubleList("percentiles").asScala.map(_.toDouble).toSeq
    new GraphiteSenderConfig(hostname, port, metricPrefix, legacySupport, envTags, tagFilter, percentiles)
  }
}

private[graphite] abstract class GraphiteSender(val senderConfig: GraphiteSenderConfig) extends Sender {
  val log: Logger = LoggerFactory.getLogger(classOf[GraphiteSender])

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    log.debug("sending metrics for interval '{}->{}' to {}", snapshot.from, snapshot.to, senderConfig)

    val timestamp = snapshot.to.getEpochSecond
    val packetBuilder = new MetricPacketBuilder(senderConfig.metricPrefix, timestamp, senderConfig)

    for {
      metric <- snapshot.counters
      instrument <- metric.instruments
    } write(packetBuilder.build(metric.name, "count", instrument.value, instrument.tags))

    for {
      metric <- snapshot.gauges
      instrument <- metric.instruments
    } write(packetBuilder.build(metric.name, "value", instrument.value, instrument.tags))

    for {
      metric <- snapshot.histograms ++ snapshot.rangeSamplers ++ snapshot.timers
      instrument <- metric.instruments
    } {
      write(packetBuilder.build(metric.name, "count", instrument.value.count, instrument.tags))
      write(packetBuilder.build(metric.name, "min", instrument.value.min, instrument.tags))
      write(packetBuilder.build(metric.name, "max", instrument.value.max, instrument.tags))
      senderConfig.percentiles.foreach { p =>
        write(packetBuilder.build(metric.name, s"p$p", instrument.value.percentile(p).value, instrument.tags))
      }
      write(packetBuilder.build(
        metric.name,
        "average",
        average(instrument.value.sum, instrument.value.count),
        instrument.tags
      ))
      write(packetBuilder.build(metric.name, "sum", instrument.value.sum, instrument.tags))
    }

    flush()
  }

  private def average(sum: Long, count: Long): Long = if (count > 0) sum / count else 0
}

private class MetricPacketBuilder(baseName: String, timestamp: Long, config: GraphiteSenderConfig) {
  private val log = LoggerFactory.getLogger(classOf[MetricPacketBuilder])
  private val builder = new java.lang.StringBuilder()
  private val tagseperator = if (config.legacySupport) '.' else ';'
  private val valueseperator = if (config.legacySupport) '.' else '='
  private val reservedChars = Set(' ', tagseperator, valueseperator)

  def build(metricName: String, metricType: String, value: Long, metricTags: TagSet): Array[Byte] =
    build(metricName, metricType, value.toString, metricTags)
  def build(metricName: String, metricType: String, value: Double, metricTags: TagSet): Array[Byte] =
    build(metricName, metricType, value.toString, metricTags)
  def build(metricName: String, metricType: String, value: String, metricTags: TagSet): Array[Byte] = {
    builder.setLength(0)
    builder.append(baseName).append(".").append(sanitize(metricName)).append(".").append(metricType)
    val allTags = metricTags.withTags(config.envTags).all()
    allTags.foreach(tag =>
      if (config.tagFilter.accept(tag.key)) {
        builder
          .append(tagseperator)
          .append(sanitizeTag(tag.key))
          .append(valueseperator)
          .append(sanitizeTag(Tag.unwrapValue(tag).toString))
      }
    )
    builder
      .append(" ")
      .append(value)
      .append(" ")
      .append(timestamp)
      .append("\n")
    val packet = builder.toString
    log.debug("built packet '{}'", packet)
    packet.getBytes(GraphiteSender.GraphiteEncoding)
  }

  private def sanitize(value: String): String =
    value.replace('/', '_').replace('.', '_')

  private def sanitizeTag(value: String): String =
    value.map(c => if (reservedChars.contains(c)) '_' else c)
}

object GraphiteSender {
  final val GraphiteEncoding: Charset = StandardCharsets.US_ASCII
}

private trait Sender extends AutoCloseable {
  def senderConfig: GraphiteSenderConfig
  def log: Logger
  def write(data: Array[Byte]): Unit
  def flush(): Unit
  def close(): Unit
}

private trait TcpSender extends Sender {
  private var out: Option[OutputStream] = None

  def write(data: Array[Byte]): Unit = {
    out match {
      case None =>
        val socket = new Socket(senderConfig.hostname, senderConfig.port)
        val stream = new BufferedOutputStream(socket.getOutputStream)
        stream.write(data)
        out = Some(stream)

      case Some(stream) =>
        stream.write(data)
    }
  }

  def flush(): Unit = out.foreach(_.flush())
  def close(): Unit = {
    try
      out.foreach { _.close() }
    catch {
      case t: Throwable => log.warn("failed to close connection", t)
    }
  }
}
