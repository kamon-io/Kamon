package kamon.instrumentation.kafka.sensor.advisor

import kamon.instrumentation.kafka.client.KafkaInstrumentation
import kamon.instrumentation.kafka.sensor.NoOpBridge
import kamon.instrumentation.kafka.sensor.advisor.SensorAdvisors.HasSensorBridge
import kamon.instrumentation.kafka.sensor.advisor.metrics.{GeneralPurposeMetric, MetricType, SensorBridgeCreator, TopicMetric}
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.apache.kafka.common.metrics.Sensor


class SensorCreateAdvisor

object SensorCreateAdvisor {

  @Advice.OnMethodExit
  def onCreatedSensor(@Advice.Argument(0) name: String, @Advice.Return sensor: Sensor with HasSensorBridge): Unit = {

    val bridge = if (KafkaInstrumentation.settings.enableSensorMetrics) {
      MetricType(name) match {
        case topicMetric: TopicMetric => SensorBridgeCreator.retrieveSpecializedSensorBridge(topicMetric)
        case generalMetric: GeneralPurposeMetric => SensorBridgeCreator.retrieveSpecializedSensorBridge(generalMetric)
      }
    } else {
      NoOpBridge
    }
    sensor.setMetric(bridge)
  }
}
