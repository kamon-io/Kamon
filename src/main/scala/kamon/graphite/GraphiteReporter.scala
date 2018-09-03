package kamon.graphite

import java.io.BufferedOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.util.{EnvironmentTagBuilder, Matcher}
import kamon.{Kamon, MetricReporter, Tags}
import org.slf4j.{Logger, LoggerFactory}

class GraphiteReporter extends MetricReporter {
  private val log = LoggerFactory.getLogger(classOf[GraphiteReporter])
  private var sender: GraphiteSender = _

  override def start(): Unit = {
    val c = GraphiteSenderConfig(Kamon.config())
    log.info("starting Graphite reporter {}", c)
    buildNewSender(c)
  }

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {
    val c = GraphiteSenderConfig(config)
    log.info("restarting new Graphite reporter {}", c)
    buildNewSender(c)
  }

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    try {
      sender.reportPeriodSnapshot(snapshot)
    } catch {
      case e: Throwable =>
        log.warn("sending failed - dispose current snapshot and retry sending next snapshot using a new connection", e)
        sender.close()
        buildNewSender(GraphiteSenderConfig(Kamon.config()))
    }
  }

  private def buildNewSender(c: GraphiteSenderConfig): Unit = sender = new GraphiteSender(c) with TcpSender
}

private case class GraphiteSenderConfig(hostname: String, port: Int, metricPrefix: String, legacySupport: Boolean, envTags: Map[String, String], tagFilter: Matcher)
private object GraphiteSenderConfig {
  def apply(config: Config): GraphiteSenderConfig = {
    val graphiteConfig = config.getConfig("kamon.graphite")
    val hostname = graphiteConfig.getString("hostname")
    val port = graphiteConfig.getInt("port")
    val metricPrefix = graphiteConfig.getString("metric-name-prefix")
    val legacySupport = graphiteConfig.getBoolean("legacy-support")
    val envTags = EnvironmentTagBuilder.create(graphiteConfig.getConfig("additional-tags"))
    val tagFilter = Kamon.filter(graphiteConfig.getString("filter-config-key"))
    GraphiteSenderConfig(hostname, port, metricPrefix, legacySupport, envTags, tagFilter)
  }
}

private abstract class GraphiteSender(val senderConfig: GraphiteSenderConfig) extends Sender {
  val log: Logger = LoggerFactory.getLogger(classOf[GraphiteSender])

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    log.debug("sending metrics for interval '{}->{}' to {}", snapshot.from, snapshot.to, senderConfig)

    val timestamp = snapshot.to.getEpochSecond
    val packetBuilder = new MetricPacketBuilder(senderConfig.metricPrefix, timestamp, senderConfig)

    for (metric <- snapshot.metrics.counters) {
      write(packetBuilder.build(metric.name, "count", metric.value, metric.tags))
    }

    for (metric <- snapshot.metrics.gauges) {
      write(packetBuilder.build(metric.name, "value", metric.value, metric.tags))
    }

    for (metric <- snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers) {
      val distribution = metric.distribution
      write(packetBuilder.build(metric.name, "count", distribution.count, metric.tags))
      write(packetBuilder.build(metric.name, "min", distribution.min, metric.tags))
      write(packetBuilder.build(metric.name, "max", distribution.max, metric.tags))
      //todo configure which percentiles should be included
      //write(packetBuilder.build(metric.name, "p50", distribution.percentile(50D).value, metric.tags))
      write(packetBuilder.build(metric.name, "p90", distribution.percentile(90D).value, metric.tags))
      //write(packetBuilder.build(metric.name, "p99", distribution.percentile(99D).value, metric.tags))
      write(packetBuilder.build(metric.name, "average", average(distribution.sum, distribution.count), metric.tags))
      write(packetBuilder.build(metric.name, "sum", distribution.sum, metric.tags))
    }

    flush()
  }

  private def average(sum: Long, count: Long): Long = if (count > 0) sum / count else 0
}

private class MetricPacketBuilder(baseName: String, timestamp: Long, config: GraphiteSenderConfig) {
  private val log = LoggerFactory.getLogger(classOf[MetricPacketBuilder])
  private val builder = new java.lang.StringBuilder()
  private val tagseperator = if (config.legacySupport) "." else ";"
  private val valueseperator = if (config.legacySupport) "." else "="

  private def sanitize(value: String): String =
    value.replace('/', '_').replace('.', '_')

  def build(metricName: String, metricType: String, value: Long, metricTags: Tags): Array[Byte] = {
    builder.setLength(0)
    builder.append(baseName).append(".").append(sanitize(metricName)).append(".").append(metricType)
    (metricTags ++ config.envTags).filterKeys(config.tagFilter.accept).foreach(kv => builder.append(tagseperator).append(kv._1).append(valueseperator).append(kv._2))
    builder
        .append(" ")
        .append(value)
        .append(" ")
        .append(timestamp)
        .append("\n")
    val packet = builder.toString
    log.debug("built packet '{}'", packet)
    packet.getBytes(StandardCharsets.US_ASCII)
  }
}

private trait Sender extends AutoCloseable {
  def senderConfig: GraphiteSenderConfig
  def log: Logger
  def write(data: Array[Byte]): Unit
  def flush(): Unit
  def close(): Unit
}

private trait TcpSender extends Sender {
  private lazy val out = {
    val socket = new Socket(senderConfig.hostname, senderConfig.port)
    new BufferedOutputStream(socket.getOutputStream)
  }

  def write(data: Array[Byte]): Unit = out.write(data)
  def flush(): Unit = out.flush()
  def close(): Unit = {
    try
      out.close()
    catch {
      case t: Throwable => log.warn("failed to close connection", t)
    }
  }
}
