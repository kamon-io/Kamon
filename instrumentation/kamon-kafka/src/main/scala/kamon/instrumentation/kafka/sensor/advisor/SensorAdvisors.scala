package kamon.instrumentation.kafka.sensor.advisor

import kamon.instrumentation.kafka.sensor.SensorBridge
import SensorAdvisors.HasSensorBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.metrics.Sensor.RecordingLevel
import org.apache.kafka.common.metrics.{KafkaMetric, MetricConfig, Sensor}

object SensorAdvisors {

  trait HasSensorBridge {
    def getMetric: SensorBridge
    def setMetric(metric: SensorBridge):Unit
  }

  class SensorMixin extends HasSensorBridge {
    private var _metric: SensorBridge = _

    override def getMetric: SensorBridge = _metric
    override def setMetric(metric: SensorBridge): Unit = _metric = metric
  }
}


class SensorRecordAdvice

object SensorRecordAdvice {
  @Advice.OnMethodEnter
  def onSensorRecord(
                      @Advice.Argument(0) value: Double,
                      @Advice.This sensor: Sensor with HasSensorBridge,
                      @Advice.FieldValue("recordingLevel") sensorRecordingLevel: RecordingLevel,
                      @Advice.FieldValue("config") metricConfig: MetricConfig,
                      @Advice.FieldValue("metrics") metrics: java.util.Map[MetricName, KafkaMetric]
                    ): Unit =
    //TODO think if we should keep this or record everything
    if (sensorRecordingLevel.shouldRecord(metricConfig.recordLevel().id)) {
      sensor.getMetric.record(value)
    }
}
