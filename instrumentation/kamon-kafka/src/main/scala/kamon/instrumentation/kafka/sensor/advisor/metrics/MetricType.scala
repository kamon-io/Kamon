package kamon.instrumentation.kafka.sensor.advisor.metrics

sealed trait MetricType

case class TopicMetric(topic: String, metric: String) extends MetricType
case class GeneralPurposeMetric(name: String) extends MetricType

object MetricType {

  def apply(metricName: String): MetricType = {
    metricName match {
      case name if isTopicMetric(name) => createTopicMetric(name)
      case name => GeneralPurposeMetric(name)
    }
  }

  def isTopicMetric(metricName: String): Boolean = {
    metricName.split("\\.") match {
      case Array("topic", _, _) => true
      case _ => false
    }
  }

  def createTopicMetric(metricName: String): MetricType = {
    metricName.split("\\.") match {
      case Array("topic", topicName, metricName) => TopicMetric(topicName, metricName)
      case _ => GeneralPurposeMetric(metricName)
    }
  }
}
