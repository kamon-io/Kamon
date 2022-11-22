package kamon.instrumentation.kafka.sensor.advisor.metrics

import kamon.Kamon
import kamon.instrumentation.kafka.sensor.{GaugeBridge, SensorBridge}
import kamon.metric.MeasurementUnit

trait SensorBridgeCreator[T <: MetricType] {
  def createSensorBridge(metricType: T): SensorBridge
}

object SensorBridgeCreator {

  def retrieveSpecializedSensorBridge[T <: MetricType : SensorBridgeCreator](metric: T): SensorBridge = {
    implicitly[SensorBridgeCreator[T]].createSensorBridge(metric)
  }

  implicit val generalMetricSensorCreator: SensorBridgeCreator[GeneralPurposeMetric] = (metricType: GeneralPurposeMetric) =>
    GaugeBridge(Kamon.gauge(metricType.name).withoutTags())

  implicit val topicMetricSensorCreator: SensorBridgeCreator[TopicMetric] = (metricType: TopicMetric) =>
    metricType.metric match {
      // TODO Match more metrics by the name and create specialized Kamon Metrics
      case "bytes" => GaugeBridge(
        Kamon
          .gauge("topic.bytes", "Number of bytes processed by the Kafka Client", MeasurementUnit.information.bytes)
          .withTag("topicName", metricType.topic)
      )
      case metricName => GaugeBridge(
        Kamon
          .gauge(s"topic.$metricName", "Topic related gauge")
          .withTag("topicName", metricType.topic)
      )
    }
}
