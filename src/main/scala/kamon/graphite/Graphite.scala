package kamon.graphite

import java.net.Socket
import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.{ Kamon, MetricReporter }
import org.slf4j.{ Logger, LoggerFactory }

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

private case class GraphiteSenderConfig(hostname: String, port: Int, metricPrefix: String)
private object GraphiteSenderConfig {
  def apply(config: Config): GraphiteSenderConfig = {
    val graphiteConfig = config.getConfig("kamon.graphite")
    val hostname = graphiteConfig.getString("hostname")
    val port = graphiteConfig.getInt("port")
    val metricPrefix = graphiteConfig.getString("metric-name-prefix")
    GraphiteSenderConfig(hostname, port, metricPrefix)
  }
}

private abstract class GraphiteSender(val senderConfig: GraphiteSenderConfig) extends Sender {
  val log: Logger = LoggerFactory.getLogger(classOf[GraphiteSender])

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    log.debug("sending {} to {}", snapshot: Any, senderConfig: Any)

    val timestamp = snapshot.to.getEpochSecond
    val packetBuilder = new MetricPacketBuilder(senderConfig.metricPrefix, timestamp)

    for (metric <- snapshot.metrics.counters) {
      write(packetBuilder.build(metric.name, "count", metric.value))
    }

    for (metric <- snapshot.metrics.gauges) {
      write(packetBuilder.build(metric.name, "value", metric.value))
    }

    for (metric <- snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers) {
      val distribution = metric.distribution
      write(packetBuilder.build(metric.name, "count", distribution.count))
      write(packetBuilder.build(metric.name, "min", distribution.min))
      write(packetBuilder.build(metric.name, "max", distribution.max))
      write(packetBuilder.build(metric.name, "p50", distribution.percentile(50D).value))
      write(packetBuilder.build(metric.name, "p90", distribution.percentile(90D).value))
      write(packetBuilder.build(metric.name, "p99", distribution.percentile(99D).value))
      write(packetBuilder.build(metric.name, "sum", distribution.count))
    }

    flush()
  }
}

private class MetricPacketBuilder(baseName: String, timestamp: Long) {
  private val log = LoggerFactory.getLogger(classOf[MetricPacketBuilder])
  private val builder = new java.lang.StringBuilder()

  private def sanitize(value: String): String =
    value.replace('/', '_').replace('.', '_')

  def build(metricName: String, metricType: String, value: Long): Array[Byte] = {
    builder.setLength(0)
    builder
      .append(baseName).append(".").append(sanitize(metricName)).append(".").append(metricType)
      .append(" ")
      .append(value)
      .append(" ")
      .append(timestamp)
      .append("\n")
    val packet = builder.toString
    log.trace("built packet '{}'", packet)
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
    socket.getOutputStream
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
