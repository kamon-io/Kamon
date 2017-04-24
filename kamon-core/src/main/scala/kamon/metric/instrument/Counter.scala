package kamon.metric.instrument

import kamon.metric.Entity

trait Counter {
  def increment(): Unit
}

object Counter {
  def apply(entity: Entity, name: String): Counter = ???
}
