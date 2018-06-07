package kamon.graphite

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.{Kamon, MetricReporter}
import org.slf4j.LoggerFactory

class GraphiteReporter extends MetricReporter {
  private val log = LoggerFactory.getLogger(classOf[GraphiteReporter])
  private var sender: GraphiteSender = _

  override def start(): Unit = {
    val c = GraphiteSenderConfig(Kamon.config())
    log.info("starting Graphite reporter {}", c)
    sender = new GraphiteSender(c)
  }
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {
    val c = GraphiteSenderConfig(config)
    log.info("restarting new Graphite reporter {}", c)
    sender = new GraphiteSender(c)
  }
  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = sender.reportPeriodSnapshot(snapshot)
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

private class GraphiteSender(val senderConfig: GraphiteSenderConfig) extends TcpSender {
  private val log = LoggerFactory.getLogger(classOf[GraphiteSender])

  def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    val timestamp = snapshot.to.getEpochSecond
    val packetBuilder = new MetricPacketBuilder(senderConfig.metricPrefix, timestamp)

    for (metric <- snapshot.metrics.counters ++ snapshot.metrics.gauges) {
      write(packetBuilder.build(metric.name, metric.value))
    }

    for (metric <- snapshot.metrics.histograms ++ snapshot.metrics.rangeSamplers) {
      val distribution = metric.distribution
      write(packetBuilder.build(metric.name + ".count", distribution.count))
      write(packetBuilder.build(metric.name + ".min", distribution.min))
      write(packetBuilder.build(metric.name + ".max", distribution.max))
      write(packetBuilder.build(metric.name + ".p50", distribution.percentile(50D).value))
      write(packetBuilder.build(metric.name + ".p90", distribution.percentile(90D).value))
      write(packetBuilder.build(metric.name + ".p99", distribution.percentile(99D).value))
      write(packetBuilder.build(metric.name + ".sum", distribution.count))
    }

    flush()
  }
}

class MetricPacketBuilder(baseName: String, timestamp: Long) {
  private val builder = new java.lang.StringBuilder()

  private def sanitize(value: String): String =
    value.replace('/', '_').replace('.', '_')

  def build(metricName: String, value: Long): Array[Byte] = {
    builder.setLength(0)
    builder
        .append(sanitize(baseName)).append(".").append(sanitize(metricName))
        .append(" ")
        .append(value)
        .append(" ")
        .append(timestamp)
        .append("\n")
    builder.toString.getBytes(StandardCharsets.US_ASCII)
  }
}

private trait TcpSender {
  def senderConfig: GraphiteSenderConfig
  def write(data: Array[Byte]): Unit = {}
  def flush(): Unit = {}

  //  object EchoClient {
  //    def main(args : Array[String]) : Unit = {
  //      for { connection <- ManagedResource(new Socket("localhost", 8007))
  //            outStream <- ManagedResource(connection.getOutputStream))
  //        val out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outStream)))
  //        inStream <- managed(new InputStreamReader(connection.getInputStream))
  //        val in = new BufferedReader(inStream)
  //      } {
  //        out.println("Test Echo Server!")
  //        out.flush()
  //        println("Client Received: " + in.readLine)
  //      }
  //    }
  //  }
}