package kamon.instrumentation.kafka.internal

import java.util
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ScheduledFuture, TimeUnit}

import kamon.Kamon
import org.apache.kafka.common.metrics.{KafkaMetric, MetricsReporter}

import scala.concurrent.duration._
import scala.util.Try

/**
  * Using kafka Client default metric listener to extract data.
  * In order to have access to them, first you must register KafkaInternalInstrumentation as a listener
  * using the "metric.reporters" property when creating a KafkaProducer or KafkaConsumer
  *
  * The key name is also available at org.apache.kafka.clients.CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG
  *
  * More details about the exposed metrics: https://docs.confluent.io/current/kafka/monitoring.html
  */
class KafkaInternalInstrumentation extends MetricsReporter {

  import KafkaInternalInstrumentation._

  private[internal] var metricPrefix = ""
  private[internal] val metrics: AtomicReference[List[KafkaGauge]] = new AtomicReference(List.empty)
  private[internal] val updater: AtomicReference[Option[ScheduledFuture[_]]] = new AtomicReference(None)
  private[internal] val doUpdate: Runnable = () => metrics.get().foreach(_.update())

  def add(metric: KafkaMetric): Unit = {
    val mn = metric.metricName
    if (!metricBlacklist.contains(mn.name)) {
      val bridge = KafkaGauge(metric, metricPrefix)
      metrics.getAndUpdate((l: List[KafkaGauge]) => bridge +: l)
    }
  }

  override def configure(configs: util.Map[String, _]): Unit = {
    val configValue = for {
      configValue <- Option(configs.get(reportIntervalKey))
      interval <- Try(configValue.toString.toLong).toOption
    } yield interval
    val interval = configValue.getOrElse(defaultReportInterval)

    updater.set(Some(Kamon.scheduler().scheduleAtFixedRate(doUpdate, interval, interval, TimeUnit.MILLISECONDS)))
    metricPrefix = Option(configs.get(metricPrefixKey)).map(_.toString).getOrElse("kafka")
  }

  override def init(metrics: util.List[KafkaMetric]): Unit = metrics.forEach(add)

  override def metricChange(metric: KafkaMetric): Unit = {
    metricRemoval(metric)
    add(metric)
  }

  override def metricRemoval(metric: KafkaMetric): Unit = {
    val oldMetrics = metrics.getAndUpdate((l: List[KafkaGauge]) => l.filterNot(_.kafkaMetric == metric))
    oldMetrics.find(_.kafkaMetric == metric).foreach(_.remove())
  }

  override def close(): Unit = updater.get().foreach(_.cancel(true))
}

object KafkaInternalInstrumentation {

  private[internal] val metricBlacklist       = Set("commit-id", "version")
  private[internal] val defaultReportInterval = 1.second.toMillis

  private[internal] val reportIntervalKey     = "kamon.instrumentation.kafka.internal.metrics-sample-interval.ms"
  private[internal] val metricPrefixKey       = "kamon.instrumentation.kafka.internal.metrics.prefix"

  def kafkaReporterValue(): String = {
    KafkaInternalInstrumentation.getClass.getCanonicalName.split("\\$").last
  }
}
