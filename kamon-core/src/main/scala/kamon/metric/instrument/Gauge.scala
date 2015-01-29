package kamon.metric.instrument

import java.util.concurrent.atomic.{ AtomicLong, AtomicLongFieldUpdater, AtomicReference }

import akka.actor.Cancellable
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.Histogram.DynamicRange

import scala.concurrent.duration.FiniteDuration

trait Gauge extends Instrument {
  type SnapshotType = Histogram.Snapshot

  def record(value: Long): Unit
  def record(value: Long, count: Long): Unit
  def refreshValue(): Unit
}

object Gauge {

  def apply(dynamicRange: DynamicRange, refreshInterval: FiniteDuration, scheduler: RefreshScheduler, valueCollector: CurrentValueCollector): Gauge = {
    val underlyingHistogram = Histogram(dynamicRange)
    val gauge = new HistogramBackedGauge(underlyingHistogram, valueCollector)
    val refreshValuesSchedule = scheduler.schedule(refreshInterval, () ⇒ {
      gauge.refreshValue()
    })

    gauge.automaticValueCollectorSchedule.set(refreshValuesSchedule)
    gauge
  }

  def create(dynamicRange: DynamicRange, refreshInterval: FiniteDuration, scheduler: RefreshScheduler, valueCollector: CurrentValueCollector): Gauge =
    apply(dynamicRange, refreshInterval, scheduler, valueCollector)

  trait CurrentValueCollector {
    def currentValue: Long
  }

  implicit def functionZeroAsCurrentValueCollector(f: () ⇒ Long): CurrentValueCollector = new CurrentValueCollector {
    def currentValue: Long = f.apply()
  }
}

/**
 *  Helper for cases in which a gauge shouldn't store the current value of a observed value but the difference between
 *  the current observed value and the previously observed value. Should only be used if the observed value is always
 *  increasing or staying steady, but is never able to decrease.
 *
 *  Note: The first time a value is collected, this wrapper will always return zero, afterwards, the difference between
 *        the current value and the last value will be returned.
 */
class DifferentialValueCollector(wrappedValueCollector: CurrentValueCollector) extends CurrentValueCollector {
  @volatile private var _readAtLeastOnce = false
  private val _lastObservedValue = new AtomicLong(0)

  def currentValue: Long = {
    if (_readAtLeastOnce) {
      val wrappedCurrent = wrappedValueCollector.currentValue
      val diff = wrappedCurrent - _lastObservedValue.getAndSet(wrappedCurrent)

      if (diff >= 0) diff else 0L

    } else {
      _lastObservedValue.set(wrappedValueCollector.currentValue)
      _readAtLeastOnce = true
      0L
    }

  }
}

object DifferentialValueCollector {
  def apply(wrappedValueCollector: CurrentValueCollector): CurrentValueCollector =
    new DifferentialValueCollector(wrappedValueCollector)

  def apply(wrappedValueCollector: ⇒ Long): CurrentValueCollector =
    new DifferentialValueCollector(new CurrentValueCollector {
      def currentValue: Long = wrappedValueCollector
    })
}

class HistogramBackedGauge(underlyingHistogram: Histogram, currentValueCollector: Gauge.CurrentValueCollector) extends Gauge {
  private[kamon] val automaticValueCollectorSchedule = new AtomicReference[Cancellable]()

  def record(value: Long): Unit = underlyingHistogram.record(value)

  def record(value: Long, count: Long): Unit = underlyingHistogram.record(value, count)

  def collect(context: CollectionContext): Histogram.Snapshot = underlyingHistogram.collect(context)

  def cleanup: Unit = {
    if (automaticValueCollectorSchedule.get() != null)
      automaticValueCollectorSchedule.get().cancel()
  }

  def refreshValue(): Unit =
    underlyingHistogram.record(currentValueCollector.currentValue)

}

