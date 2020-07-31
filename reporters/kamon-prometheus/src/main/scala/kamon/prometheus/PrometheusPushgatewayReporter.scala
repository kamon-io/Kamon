package kamon.prometheus

import java.time.Duration

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.module.{MetricReporter, Module, ModuleFactory}
import org.slf4j.LoggerFactory

class PrometheusPushgatewayReporter(
    configPath: String,
    pushgatewayPath: String,
    @volatile private var httpClientFactory: Config => HttpClient
  ) extends MetricReporter {

  private val _logger = LoggerFactory.getLogger(classOf[PrometheusPushgatewayReporter])
  private val _snapshotAccumulator = PeriodSnapshot.accumulator(Duration.ofDays(365 * 5), Duration.ZERO, Duration.ofDays(365 * 5))

  @volatile private var httpClient: HttpClient = _
  @volatile private var settings: PrometheusSettings.Generic = _

  {
    reconfigure(Kamon.config())
  }

  def this(httpClientFactory: Config => HttpClient) =
    this("kamon.prometheus", "pushgateway", httpClientFactory)

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
    _snapshotAccumulator.add(snapshot)
    val currentData = _snapshotAccumulator.peek()

    val scrapeDataBuilder = new ScrapeDataBuilder(settings, PrometheusSettings.environmentTags(settings))
    scrapeDataBuilder.appendCounters(currentData.counters)
    scrapeDataBuilder.appendGauges(currentData.gauges)
    scrapeDataBuilder.appendHistograms(currentData.histograms)
    scrapeDataBuilder.appendHistograms(currentData.timers)
    scrapeDataBuilder.appendHistograms(currentData.rangeSamplers)

    val message = scrapeDataBuilder.build()

    httpClient.doPost("text/plain; version=0.0.4", message.toCharArray.map(_.toByte)).failed.foreach(
      exception => _logger.error("Failed to send metrics to Prometheus Pushgateway", exception)
    )
  }

  override def stop(): Unit = ()

  override def reconfigure(newConfig: Config): Unit = {
    this.settings = PrometheusSettings.readSettings(newConfig.getConfig(configPath))
    this.httpClient = httpClientFactory(newConfig.getConfig(configPath).getConfig(pushgatewayPath))
    _logger.info(s"Connection to Prometheus Pushgateway on url ${this.httpClient.apiUrl}")
  }
}

object PrometheusPushgatewayReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      new PrometheusPushgatewayReporter((config: Config) => new HttpClient(config))
    }
  }
}
