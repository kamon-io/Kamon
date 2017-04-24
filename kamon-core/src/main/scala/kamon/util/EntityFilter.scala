package kamon.util

import kamon.metric.Entity

trait EntityFilter {
  def accept(entity: Entity): Boolean
}
