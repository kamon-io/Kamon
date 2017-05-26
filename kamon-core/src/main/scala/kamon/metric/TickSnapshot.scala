package kamon.metric

import java.time.Instant

import kamon.metric.instrument.{DistributionSnapshot, SingleValueSnapshot}

case class Interval(from: Instant, to: Instant)

case class MetricsSnapshot(
  histograms: Seq[DistributionSnapshot],
  minMaxCounters: Seq[DistributionSnapshot],
  gauges: Seq[SingleValueSnapshot],
  counters: Seq[SingleValueSnapshot]
)

case class TickSnapshot(interval: Interval, metrics: MetricsSnapshot)


