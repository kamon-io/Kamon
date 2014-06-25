package kamon.metric.instrument

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ Cancellable, ActorSystem }
import com.typesafe.config.Config
import kamon.metric.{ CollectionContext, Scale, MetricRecorder }

import scala.concurrent.duration.FiniteDuration

trait Gauge extends MetricRecorder {
  type SnapshotType = Histogram.Snapshot

  def record(value: Long)
  def record(value: Long, count: Long)
}

object Gauge {

  trait CurrentValueCollector {
    def currentValue: Long
  }

  def apply(precision: Histogram.Precision, highestTrackableValue: Long, scale: Scale, refreshInterval: FiniteDuration,
    system: ActorSystem)(currentValueCollector: CurrentValueCollector): Gauge = {

    val underlyingHistogram = Histogram(highestTrackableValue, precision, scale)
    val gauge = new HistogramBackedGauge(underlyingHistogram, currentValueCollector)

    val refreshValuesSchedule = system.scheduler.schedule(refreshInterval, refreshInterval) {
      gauge.refreshValue()
    }(system.dispatcher) // TODO: Move this to Kamon dispatchers

    gauge.refreshValuesSchedule.set(refreshValuesSchedule)
    gauge
  }

  def fromDefaultConfig(system: ActorSystem)(currentValueCollectorFunction: () ⇒ Long): Gauge =
    fromDefaultConfig(system, functionZeroAsCurrentValueCollector(currentValueCollectorFunction))

  def fromDefaultConfig(system: ActorSystem, currentValueCollector: CurrentValueCollector): Gauge = {
    val config = system.settings.config.getConfig("kamon.metrics.precision.default-gauge-precision")
    fromConfig(config, system)(currentValueCollector)
  }

  def fromConfig(config: Config, system: ActorSystem, scale: Scale)(currentValueCollector: CurrentValueCollector): Gauge = {
    import scala.concurrent.duration._

    val highest = config.getLong("highest-trackable-value")
    val significantDigits = config.getInt("significant-value-digits")
    val refreshInterval = config.getDuration("refresh-interval", TimeUnit.MILLISECONDS)

    Gauge(Histogram.Precision(significantDigits), highest, scale, refreshInterval.millis, system)(currentValueCollector)
  }

  def fromConfig(config: Config, system: ActorSystem)(currentValueCollector: CurrentValueCollector): Gauge = {
    fromConfig(config, system, Scale.Unit)(currentValueCollector)
  }

  implicit def functionZeroAsCurrentValueCollector(f: () ⇒ Long): CurrentValueCollector = new CurrentValueCollector {
    def currentValue: Long = f.apply()
  }
}

class HistogramBackedGauge(underlyingHistogram: Histogram, currentValueCollector: Gauge.CurrentValueCollector) extends Gauge {
  val refreshValuesSchedule = new AtomicReference[Cancellable]()

  def record(value: Long): Unit = underlyingHistogram.record(value)

  def record(value: Long, count: Long): Unit = underlyingHistogram.record(value, count)

  def collect(context: CollectionContext): Histogram.Snapshot = underlyingHistogram.collect(context)

  def cleanup: Unit = {
    if (refreshValuesSchedule.get() != null)
      refreshValuesSchedule.get().cancel()
  }

  def refreshValue(): Unit = underlyingHistogram.record(currentValueCollector.currentValue)
}

