package kamon.prometheus

import com.typesafe.config.Config
import kamon.{Kamon, MetricReporter}
import kamon.metric._
import kamon.util.MeasurementUnit

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

class PrometheusReporter extends MetricReporter {

  override def start(): Unit = {}

  override def stop(): Unit = {}

  override def reconfigure(config: Config): Unit = {}

  override def reportTickSnapshot(snapshot: TickSnapshot): Unit = {
    snapshot.metrics.counters.foreach(accumulateValue(this.counters, _))
    snapshot.metrics.gauges.foreach(accumulateValue(this.gauges, _))
    snapshot.metrics.histograms.foreach(accumulateDistribution(this.histograms, _))
    snapshot.metrics.minMaxCounters.foreach(accumulateDistribution(this.histograms, _))

    val scrapeDataBuilder = new ScrapeDataBuilder(readConfiguration(Kamon.config()))
    scrapeDataBuilder.appendCounters(counters.values.map(_.snapshot()).toSeq)
    scrapeDataBuilder.appendGauges(gauges.values.map(_.snapshot()).toSeq)
    scrapeDataBuilder.appendHistograms(histograms.values.map(_.snapshot()).toSeq)

    println(scrapeDataBuilder.build())

  }

  private val counters = TrieMap.empty[MetricKey, CumulativeValue]
  private val gauges = TrieMap.empty[MetricKey, CumulativeValue]
  private val histograms = TrieMap.empty[MetricKey, CumulativeDistribution]


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



  private def readConfiguration(config: Config): PrometheusReporter.Configuration = {
    val prometheusConfig = config.getConfig("kamon.prometheus")

    PrometheusReporter.Configuration(
      startEmbeddedServer = prometheusConfig.getBoolean("start-embedded-server"),
      embeddedServerPort = prometheusConfig.getInt("embedded-server.port"),
      defaultBuckets = prometheusConfig.getDoubleList("buckets.default-buckets").asScala,
      timeBuckets = prometheusConfig.getDoubleList("buckets.time-buckets").asScala,
      informationBuckets = prometheusConfig.getDoubleList("buckets.information-buckets").asScala
    )
  }
}

object PrometheusReporter {
  case class Configuration(startEmbeddedServer: Boolean, embeddedServerPort: Int, defaultBuckets: Seq[java.lang.Double],
    timeBuckets: Seq[java.lang.Double], informationBuckets: Seq[java.lang.Double])
}
