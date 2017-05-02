package kamon.metric

import kamon.metric.instrument.{DistributionSnapshot, SingleValueSnapshot}

class EntitySnapshot(
  val entity: Entity,
  val histograms: Seq[DistributionSnapshot],
  val minMaxCounters: Seq[DistributionSnapshot],
  val gauges: Seq[SingleValueSnapshot],
  val counters: Seq[SingleValueSnapshot]
)