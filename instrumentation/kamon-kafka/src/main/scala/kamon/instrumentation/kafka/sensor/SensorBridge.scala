package kamon.instrumentation.kafka.sensor

import kamon.metric.Gauge

sealed trait SensorBridge {
  def record(value: Double): Unit
}

case class GaugeBridge(gauge: Gauge) extends SensorBridge {
  override def record(value: Double): Unit = gauge.update(value)
}

case object NoOpBridge extends SensorBridge {
  override def record(value: Double): Unit = {}
}
