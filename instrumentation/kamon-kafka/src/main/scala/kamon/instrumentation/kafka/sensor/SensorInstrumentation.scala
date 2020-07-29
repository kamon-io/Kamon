package kamon.instrumentation.kafka.sensor

import kamon.instrumentation.kafka.sensor.advisor.SensorAdvisors.SensorMixin
import kamon.instrumentation.kafka.sensor.advisor.{SensorCreateAdvisor, SensorRecordAdvice}
import kanela.agent.api.instrumentation.InstrumentationBuilder

class SensorInstrumentation extends InstrumentationBuilder {

  /*Instrument raw sensor factory to collect sensor name and tags.
  * Necessary since not all (but most) sensors are created through `StreamsMetricsImpl.xxxLevelSensor`
  **/
  onType("org.apache.kafka.common.metrics.Metrics")
    .advise(method("sensor").and(takesArguments(5)), classOf[SensorCreateAdvisor]) //applies SensorName, scopedSensors will have this overriden by StreamsMetricsImpl


  /*Wiretap sensor recordings and apply them to Kamon instruments.
  * Additional metrics added to sensor are different measures over same data and can be extracted from Kamon histogram
  * so there's no need for extra instruments here. Metrics might bring additional tags on top of sensor's own ones.
  * */
  onType("org.apache.kafka.common.metrics.Sensor")
    .mixin(classOf[SensorMixin])
    .advise(method("record").and(takesArguments(3)), classOf[SensorRecordAdvice])
}
