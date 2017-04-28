package kamon.metric

import java.time.Instant


trait TickSnapshot {
  def interval: Interval
  def entities: Seq[EntitySnapshot]
}

trait Interval {
  def from: Instant
  def to: Instant
}



