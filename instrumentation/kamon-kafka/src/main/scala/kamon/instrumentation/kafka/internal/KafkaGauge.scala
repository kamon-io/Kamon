package kamon.instrumentation.kafka.internal

import kamon.Kamon
import kamon.tag.TagSet
import org.apache.kafka.common.metrics.KafkaMetric

import scala.collection.JavaConverters._

case class KafkaGauge(kafkaMetric: KafkaMetric, prefix: String) {
  private[internal] val tags = TagSet.from(
    kafkaMetric.metricName.tags.asScala.mapValues(v => v.asInstanceOf[Any]).toMap
  )
  private[internal] val gauge = Kamon.gauge(name(), "").withTags(tags)

  def name(): String = {
    val mn = kafkaMetric.metricName
    s"${prefix}_${mn.group}_${mn.name}".replaceAll("-", "_")
  }

  def metricValue: Long = kafkaMetric.metricValue match {
    case d: java.lang.Double => d.toLong
    case _ => 0L
  }

  def update(): Unit = gauge.update(metricValue)

  def remove(): Unit = gauge.remove()
}
