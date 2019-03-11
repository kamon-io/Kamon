package kamon
package metric

import kamon.tag.TagSet

/**
  * Describes the common structure of a metric snapshot. Metric snapshots always contain the metric basic informantion
  * and snapshots of all instruments created for that metric.
  */
sealed trait MetricSnapshot[Sett <: Metric.Settings, Snap] {

  /**
    * Name of the metric from which the snapshot was taken.
    */
  def name: String

  /**
    * Description of the metric from which the snapshot was taken.
    */
  def description: String

  /**
    * Settings of the metric from which the snapshot was taken.
    */
  def settings: Sett

  /**
    * Instrument snapshots of all instruments associated with the metric from which the snapshot was taken.
    */
  def instruments: Map[TagSet, Snap]
}

object MetricSnapshot {

  /**
    * Concrete snapshot implementation for metrics backed by instruments that produce single values. E.g. counters and
    * gauges.
    */
  case class Value (
    name: String,
    description: String,
    settings: Metric.Settings.ValueInstrument,
    instruments: Map[TagSet, Long]
  ) extends MetricSnapshot[Metric.Settings.ValueInstrument, Long]


  /**
    * Concrete snapshot implementation for metrics backed by instruments that produce a distribution of values.
    * E.g. histograms, timers and range samplers.
    */
  case class Distribution (
    name: String,
    description: String,
    settings: Metric.Settings.DistributionInstrument,
    instruments: Map[TagSet, kamon.metric.Distribution]
  ) extends MetricSnapshot[Metric.Settings.DistributionInstrument, kamon.metric.Distribution]

}