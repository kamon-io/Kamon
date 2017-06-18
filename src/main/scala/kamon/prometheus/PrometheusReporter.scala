package kamon.prometheus

import com.typesafe.config.Config
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.{Response, newFixedLengthResponse}
import kamon.{Kamon, MetricReporter}
import kamon.metric._
import kamon.util.MeasurementUnit
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

class PrometheusReporter extends MetricReporter {
  private val logger = LoggerFactory.getLogger(classOf[PrometheusReporter])
  private var embeddedHttpServer: Option[EmbeddedHttpServer] = None
  @volatile private var preparedScrapeData: String =
    "# The kamon-prometheus module didn't receive any data just yet.\n"

  private val counters = TrieMap.empty[MetricKey, CumulativeValue]
  private val histograms = TrieMap.empty[MetricKey, CumulativeDistribution]


  override def start(): Unit = {
    val config = readConfiguration(Kamon.config())

    if(config.startEmbeddedServer)
      startEmbeddedServer(config)
  }

  override def stop(): Unit = {
    stopEmbeddedServer()
  }

  override def reconfigure(newConfig: Config): Unit = {
    val config = readConfiguration(newConfig)
    if(config.startEmbeddedServer) {
      stopEmbeddedServer()
      startEmbeddedServer(config)
    }
  }

  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = {
    snapshot.metrics.counters.foreach(accumulateValue(this.counters, _))
    snapshot.metrics.histograms.foreach(accumulateDistribution(this.histograms, _))
    snapshot.metrics.minMaxCounters.foreach(accumulateDistribution(this.histograms, _))

    val scrapeDataBuilder = new ScrapeDataBuilder(readConfiguration(Kamon.config()))
    scrapeDataBuilder.appendCounters(counters.values.map(_.snapshot()).toSeq)
    scrapeDataBuilder.appendGauges(snapshot.metrics.gauges)
    scrapeDataBuilder.appendHistograms(histograms.values.map(_.snapshot()).toSeq)

    preparedScrapeData = scrapeDataBuilder.build()
  }

  def scrapeData(): String =
    preparedScrapeData

  private def accumulateValue(target: TrieMap[MetricKey, CumulativeValue], metric: MetricValue): Unit =
    target.getOrElseUpdate(MetricKey(metric.name, metric.tags, metric.unit), new CumulativeValue(metric)).add(metric)

  private def accumulateDistribution(target: TrieMap[MetricKey, CumulativeDistribution], metric: MetricDistribution): Unit =
    target.getOrElseUpdate(MetricKey(metric.name, metric.tags, metric.unit), new CumulativeDistribution(metric)).add(metric)


  private case class MetricKey(name: String, tags: Map[String, String], unit: MeasurementUnit)

  private class CumulativeDistribution(initialDistribution: MetricDistribution) {
    private val accumulator = new DistributionAccumulator(initialDistribution.dynamicRange)

    def add(metric: MetricDistribution): Unit =
      this.accumulator.add(metric.distribution)

    def snapshot(): MetricDistribution =
      initialDistribution.copy(distribution = this.accumulator.result(resetState = false))
  }

  private class CumulativeValue(initialValue: MetricValue) {
    private var value = 0L

    def add(metric: MetricValue): Unit =
      this.value += metric.value

    def snapshot(): MetricValue =
      initialValue.copy(value = this.value)
  }

  class EmbeddedHttpServer(hostname: String, port: Int) extends NanoHTTPD(hostname, port) {
    override def serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = {
      newFixedLengthResponse(Response.Status.OK, "text/plain", scrapeData())
    }
  }


  private def startEmbeddedServer(config: PrometheusReporter.Configuration): Unit = {
    val server = new EmbeddedHttpServer(config.embeddedServerHostname, config.embeddedServerPort)
    server.start()

    logger.info(s"Started the embedded HTTP server on http://${config.embeddedServerHostname}:${config.embeddedServerPort}")
    embeddedHttpServer = Some(server)
  }

  private def stopEmbeddedServer(): Unit =
    embeddedHttpServer.foreach(_.stop())



  private def readConfiguration(config: Config): PrometheusReporter.Configuration = {
    val prometheusConfig = config.getConfig("kamon.prometheus")

    PrometheusReporter.Configuration(
      startEmbeddedServer = prometheusConfig.getBoolean("start-embedded-http-server"),
      embeddedServerHostname = prometheusConfig.getString("embedded-server.hostname"),
      embeddedServerPort = prometheusConfig.getInt("embedded-server.port"),
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala
    )
  }
}

object PrometheusReporter {
  case class Configuration(startEmbeddedServer: Boolean, embeddedServerHostname: String, embeddedServerPort: Int,
    defaultBuckets: Seq[java.lang.Double], timeBuckets: Seq[java.lang.Double], informationBuckets: Seq[java.lang.Double])
}
