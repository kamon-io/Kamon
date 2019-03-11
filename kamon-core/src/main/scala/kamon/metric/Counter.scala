package kamon.metric

import kamon.metric.Metric.{BaseMetric, BaseMetricAutoUpdate}
import kamon.tag.TagSet
import org.slf4j.LoggerFactory


/**
  * Instrument that tracks a monotonically increasing value.
  */
trait Counter extends Instrument[Counter, Metric.Settings.ValueInstrument] {

  /**
    * Increments the counter by one.
    */
  def increment(): Counter

  /**
    * Increments the counter by the provided times.
    */
  def increment(times: Long): Counter
}


object Counter {

  private val _logger = LoggerFactory.getLogger(classOf[Counter])


  /**
    * LongAdder-based counter implementation. LongAdder counters are safe to share across threads and provide superior
    * write performance over Atomic values in cases where the writes largely outweigh the reads, which is the common
    * case in Kamon counters (writing every time something needs to be tracked, reading roughly once per minute).
    */
  class LongAdder(val metric: BaseMetric[Counter, Metric.Settings.ValueInstrument,Long], val tags: TagSet)
      extends Counter with Instrument.Snapshotting[Long]
      with BaseMetricAutoUpdate[Counter, Metric.Settings.ValueInstrument,Long] {

    private val _adder = new kamon.jsr166.LongAdder()

    override def increment(): Counter = {
      _adder.increment()
      this
    }

    override def increment(times: Long): Counter = {
      if(times >= 0)
        _adder.add(times)
      else
        _logger.warn(s"Ignoring an attempt to decrease a counter by [$times] on [${metric.name},${tags}]")

      this
    }

    override def snapshot(resetState: Boolean): Long =
      if(resetState)
       _adder.sumAndReset()
      else
       _adder.sum()

    override def baseMetric: BaseMetric[Counter, Metric.Settings.ValueInstrument,Long] =
      metric
  }
}
