package kamon.metric

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet


/**
  * Instrument that tracks the latest observed value of a given measure.
  */
trait Gauge extends Instrument[Gauge, Metric.Settings.ForValueInstrument] {

  /**
    * Sets the current value of the gauge to the provided value.
    */
  def update(value: Double): Gauge

}

object Gauge {

  /**
    * Gauge implementation backed by a volatile variable.
    */
  class Volatile(val metric: BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double], val tags: TagSet) extends Gauge
      with Instrument.Snapshotting[Double] with BaseMetricAutoUpdate[Gauge, Metric.Settings.ForValueInstrument, Double] {

    @volatile private var _currentValue = 0D

    override def update(value: Double): Gauge = {
      _currentValue = value
      this
    }

    override def snapshot(resetState: Boolean): Double =
      _currentValue

    override def baseMetric: BaseMetric[Gauge, Metric.Settings.ForValueInstrument, Double] =
      metric
  }
}



