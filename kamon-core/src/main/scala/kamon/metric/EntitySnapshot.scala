package kamon.metric

import kamon.metric.instrument.{DistributionSnapshot, SingleValueSnapshot}

trait EntitySnapshot {
  def entity: Entity
  def histograms: Seq[DistributionSnapshot]
  def minMaxCounters: Seq[DistributionSnapshot]
  def gauges: Seq[SingleValueSnapshot]
  def counters: Seq[SingleValueSnapshot]
}