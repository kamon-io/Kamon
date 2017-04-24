package kamon.metric.instrument

import kamon.metric.Entity

trait Histogram {
  def dynamicRange: DynamicRange

  def record(value: Long): Unit
  def record(value: Long, times: Long): Unit
}

object Histogram {
  def apply(entity: Entity, name: String, dynamicRange2: DynamicRange): Histogram = new Histogram {
    override def dynamicRange: DynamicRange = dynamicRange2
    override def record(value: Long): Unit = ???
    override def record(value: Long, times: Long): Unit = ???
  }
}
