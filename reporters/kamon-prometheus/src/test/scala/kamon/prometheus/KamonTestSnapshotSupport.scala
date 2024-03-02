package kamon.prometheus

import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder

trait KamonTestSnapshotSupport {
  val emptyPeriodSnapshot: PeriodSnapshot = PeriodSnapshot(
    Kamon.clock().instant(),
    Kamon.clock().instant(),
    Seq.empty,
    Seq.empty,
    Seq.empty,
    Seq.empty,
    Seq.empty
  )

  def counter(metricName: String, tags: Map[String, String] = Map.empty): PeriodSnapshot =
    emptyPeriodSnapshot.copy(counters = Seq(MetricSnapshotBuilder.counter(metricName, TagSet.from(tags), 1L)))

  def gauge(metricName: String, tags: Map[String, String] = Map.empty): PeriodSnapshot =
    emptyPeriodSnapshot.copy(gauges = Seq(MetricSnapshotBuilder.gauge(metricName, TagSet.from(tags), 1d)))

  def histogram(metricName: String, tags: Map[String, String] = Map.empty): PeriodSnapshot =
    emptyPeriodSnapshot.copy(histograms = Seq(MetricSnapshotBuilder.histogram(metricName, TagSet.from(tags))(1)))

  def rangeSampler(metricName: String, tags: Map[String, String] = Map.empty): PeriodSnapshot =
    emptyPeriodSnapshot.copy(rangeSamplers = Seq(MetricSnapshotBuilder.histogram(metricName, TagSet.from(tags))(1)))

  def timers(metricName: String, tags: Map[String, String] = Map.empty): PeriodSnapshot =
    emptyPeriodSnapshot.copy(rangeSamplers = Seq(MetricSnapshotBuilder.histogram(metricName, TagSet.from(tags))(1)))
}
