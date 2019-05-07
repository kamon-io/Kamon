package kamon
package metric


/**
  * Contains snapshots of all known instruments for a given metric. Instances of this class are meant to be exposed to
  * metric reporters via the PeriodSnapshot.
  */
case class MetricSnapshot[Sett <: Metric.Settings, Snap] (
  name: String,
  description: String,
  settings: Sett,
  instruments: Seq[Instrument.Snapshot[Snap]]
)

object MetricSnapshot {

  type Values[T] = MetricSnapshot[Metric.Settings.ForValueInstrument, T]
  type Distributions = MetricSnapshot[Metric.Settings.ForDistributionInstrument, Distribution]

  /**
    * Creates a MetricSnapshot instance for metrics that produce single values.
    */
  def ofValues[T](name: String, description: String, settings: Metric.Settings.ForValueInstrument,
      instruments: Seq[Instrument.Snapshot[T]]): Values[T] = {

    MetricSnapshot(
      name,
      description,
      settings,
      instruments
    )
  }

  /**
    * Creates a MetricSnapshot instance for metrics that produce distributions.
    */
  def ofDistributions(name: String, description: String, settings: Metric.Settings.ForDistributionInstrument,
    instruments: Seq[Instrument.Snapshot[Distribution]]): Distributions = {

    MetricSnapshot(
      name,
      description,
      settings,
      instruments
    )
  }
}