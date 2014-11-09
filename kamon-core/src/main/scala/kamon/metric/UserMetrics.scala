package kamon.metric

import akka.actor
import akka.actor.{ ExtendedActorSystem, ExtensionIdProvider, ExtensionId }
import kamon.Kamon
import kamon.metric.instrument.{ Gauge, MinMaxCounter, Counter, Histogram }

import scala.concurrent.duration.FiniteDuration

class UserMetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  import Metrics.AtomicGetOrElseUpdateForTriemap
  import UserMetrics._

  lazy val metricsExtension = Kamon(Metrics)(system)
  val precisionConfig = system.settings.config.getConfig("kamon.metrics.precision")

  val defaultHistogramPrecisionConfig = precisionConfig.getConfig("default-histogram-precision")
  val defaultMinMaxCounterPrecisionConfig = precisionConfig.getConfig("default-min-max-counter-precision")
  val defaultGaugePrecisionConfig = precisionConfig.getConfig("default-gauge-precision")

  def registerHistogram(name: String, precision: Histogram.Precision, highestTrackableValue: Long): Histogram = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserHistogram(name), {
      UserHistogramRecorder(Histogram(highestTrackableValue, precision, Scale.Unit))
    }).asInstanceOf[UserHistogramRecorder].histogram
  }

  def registerHistogram(name: String): Histogram = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserHistogram(name), {
      UserHistogramRecorder(Histogram.fromConfig(defaultHistogramPrecisionConfig))
    }).asInstanceOf[UserHistogramRecorder].histogram
  }

  def registerCounter(name: String): Counter = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserCounter(name), {
      UserCounterRecorder(Counter())
    }).asInstanceOf[UserCounterRecorder].counter
  }

  def registerMinMaxCounter(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration): MinMaxCounter = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserMinMaxCounter(name), {
      UserMinMaxCounterRecorder(MinMaxCounter(highestTrackableValue, precision, Scale.Unit, refreshInterval, system))
    }).asInstanceOf[UserMinMaxCounterRecorder].minMaxCounter
  }

  def registerMinMaxCounter(name: String): MinMaxCounter = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserMinMaxCounter(name), {
      UserMinMaxCounterRecorder(MinMaxCounter.fromConfig(defaultMinMaxCounterPrecisionConfig, system))
    }).asInstanceOf[UserMinMaxCounterRecorder].minMaxCounter
  }

  def registerGauge(name: String)(currentValueCollector: Gauge.CurrentValueCollector): Gauge = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserGauge(name), {
      UserGaugeRecorder(Gauge.fromConfig(defaultGaugePrecisionConfig, system)(currentValueCollector))
    }).asInstanceOf[UserGaugeRecorder].gauge
  }

  def registerGauge(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration)(currentValueCollector: Gauge.CurrentValueCollector): Gauge = {
    metricsExtension.storage.atomicGetOrElseUpdate(UserGauge(name), {
      UserGaugeRecorder(Gauge(precision, highestTrackableValue, Scale.Unit, refreshInterval, system)(currentValueCollector))
    }).asInstanceOf[UserGaugeRecorder].gauge
  }

  def removeHistogram(name: String): Unit =
    metricsExtension.unregister(UserHistogram(name))

  def removeCounter(name: String): Unit =
    metricsExtension.unregister(UserCounter(name))

  def removeMinMaxCounter(name: String): Unit =
    metricsExtension.unregister(UserMinMaxCounter(name))

  def removeGauge(name: String): Unit =
    metricsExtension.unregister(UserGauge(name))
}

object UserMetrics extends ExtensionId[UserMetricsExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Metrics

  def createExtension(system: ExtendedActorSystem): UserMetricsExtension = new UserMetricsExtension(system)

  sealed trait UserMetricGroup
  //
  // Histograms
  //

  case class UserHistogram(name: String) extends MetricGroupIdentity with UserMetricGroup {
    val category = UserHistograms
  }

  case class UserHistogramRecorder(histogram: Histogram) extends MetricGroupRecorder {
    def collect(context: CollectionContext): MetricGroupSnapshot =
      UserHistogramSnapshot(histogram.collect(context))

    def cleanup: Unit = histogram.cleanup
  }

  case class UserHistogramSnapshot(histogramSnapshot: Histogram.Snapshot) extends MetricGroupSnapshot {
    type GroupSnapshotType = UserHistogramSnapshot

    def merge(that: UserHistogramSnapshot, context: CollectionContext): UserHistogramSnapshot =
      UserHistogramSnapshot(that.histogramSnapshot.merge(histogramSnapshot, context))

    def metrics: Map[MetricIdentity, MetricSnapshot] = Map((RecordedValues, histogramSnapshot))
  }

  //
  // Counters
  //

  case class UserCounter(name: String) extends MetricGroupIdentity with UserMetricGroup {
    val category = UserCounters
  }

  case class UserCounterRecorder(counter: Counter) extends MetricGroupRecorder {
    def collect(context: CollectionContext): MetricGroupSnapshot =
      UserCounterSnapshot(counter.collect(context))

    def cleanup: Unit = counter.cleanup
  }

  case class UserCounterSnapshot(counterSnapshot: Counter.Snapshot) extends MetricGroupSnapshot {
    type GroupSnapshotType = UserCounterSnapshot

    def merge(that: UserCounterSnapshot, context: CollectionContext): UserCounterSnapshot =
      UserCounterSnapshot(that.counterSnapshot.merge(counterSnapshot, context))

    def metrics: Map[MetricIdentity, MetricSnapshot] = Map((Count, counterSnapshot))
  }

  //
  // MinMaxCounters
  //

  case class UserMinMaxCounter(name: String) extends MetricGroupIdentity with UserMetricGroup {
    val category = UserMinMaxCounters
  }

  case class UserMinMaxCounterRecorder(minMaxCounter: MinMaxCounter) extends MetricGroupRecorder {
    def collect(context: CollectionContext): MetricGroupSnapshot =
      UserMinMaxCounterSnapshot(minMaxCounter.collect(context))

    def cleanup: Unit = minMaxCounter.cleanup
  }

  case class UserMinMaxCounterSnapshot(minMaxCounterSnapshot: Histogram.Snapshot) extends MetricGroupSnapshot {
    type GroupSnapshotType = UserMinMaxCounterSnapshot

    def merge(that: UserMinMaxCounterSnapshot, context: CollectionContext): UserMinMaxCounterSnapshot =
      UserMinMaxCounterSnapshot(that.minMaxCounterSnapshot.merge(minMaxCounterSnapshot, context))

    def metrics: Map[MetricIdentity, MetricSnapshot] = Map((RecordedValues, minMaxCounterSnapshot))
  }

  //
  // Gauges
  //

  case class UserGauge(name: String) extends MetricGroupIdentity with UserMetricGroup {
    val category = UserGauges
  }

  case class UserGaugeRecorder(gauge: Gauge) extends MetricGroupRecorder {
    def collect(context: CollectionContext): MetricGroupSnapshot =
      UserGaugeSnapshot(gauge.collect(context))

    def cleanup: Unit = gauge.cleanup
  }

  case class UserGaugeSnapshot(gaugeSnapshot: Histogram.Snapshot) extends MetricGroupSnapshot {
    type GroupSnapshotType = UserGaugeSnapshot

    def merge(that: UserGaugeSnapshot, context: CollectionContext): UserGaugeSnapshot =
      UserGaugeSnapshot(that.gaugeSnapshot.merge(gaugeSnapshot, context))

    def metrics: Map[MetricIdentity, MetricSnapshot] = Map((RecordedValues, gaugeSnapshot))
  }

  case object UserHistograms extends MetricGroupCategory { val name: String = "histogram" }
  case object UserCounters extends MetricGroupCategory { val name: String = "counter" }
  case object UserMinMaxCounters extends MetricGroupCategory { val name: String = "min-max-counter" }
  case object UserGauges extends MetricGroupCategory { val name: String = "gauge" }

  case object RecordedValues extends MetricIdentity { val name: String = "values" }
  case object Count extends MetricIdentity { val name: String = "count" }

}

