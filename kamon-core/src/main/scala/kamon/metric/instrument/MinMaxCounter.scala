package kamon.metric.instrument

import java.time.Duration

import kamon.metric.Entity

trait MinMaxCounter {
  def dynamicRange: DynamicRange
  def sampleInterval: Duration

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement(): Unit
  def decrement(times: Long): Unit
}

object MinMaxCounter {
  def apply(entity: Entity, name: String, dynamicRange2: DynamicRange, sampleInterval2: Duration): MinMaxCounter = new MinMaxCounter {
    override def sampleInterval: Duration = sampleInterval2
    override def increment(): Unit = ???
    override def increment(times: Long): Unit = ???
    override def decrement(): Unit = ???
    override def decrement(times: Long): Unit = ???
    override def dynamicRange: DynamicRange = dynamicRange2
  }
}
