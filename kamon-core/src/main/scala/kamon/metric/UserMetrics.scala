package kamon.metric

import akka.actor
import akka.actor.{ ActorSystem, ExtendedActorSystem, ExtensionIdProvider, ExtensionId }
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.instrument.{ Gauge, MinMaxCounter, Counter, Histogram }

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

class UserMetricsExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  lazy val userMetricsRecorder = Kamon(Metrics)(system).register(UserMetrics, UserMetrics.Factory).get

  def registerHistogram(name: String, precision: Histogram.Precision, highestTrackableValue: Long): Histogram =
    userMetricsRecorder.buildHistogram(name, precision, highestTrackableValue)

  def registerHistogram(name: String): Histogram =
    userMetricsRecorder.buildHistogram(name)

  def registerCounter(name: String): Counter =
    userMetricsRecorder.buildCounter(name)

  def registerMinMaxCounter(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration): MinMaxCounter = {
    userMetricsRecorder.buildMinMaxCounter(name, precision, highestTrackableValue, refreshInterval)
  }

  def registerMinMaxCounter(name: String): MinMaxCounter =
    userMetricsRecorder.buildMinMaxCounter(name)

  def registerGauge(name: String)(currentValueCollector: Gauge.CurrentValueCollector): Gauge =
    userMetricsRecorder.buildGauge(name)(currentValueCollector)

  def registerGauge(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
    refreshInterval: FiniteDuration)(currentValueCollector: Gauge.CurrentValueCollector): Gauge =
    userMetricsRecorder.buildGauge(name, precision, highestTrackableValue, refreshInterval, currentValueCollector)
}

object UserMetrics extends ExtensionId[UserMetricsExtension] with ExtensionIdProvider with MetricGroupIdentity {
  def lookup(): ExtensionId[_ <: actor.Extension] = Metrics
  def createExtension(system: ExtendedActorSystem): UserMetricsExtension = new UserMetricsExtension(system)

  val name: String = "user-metrics-recorder"
  val category = new MetricGroupCategory {
    val name: String = "user-metrics"
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = UserMetricsRecorder
    def create(config: Config, system: ActorSystem): UserMetricsRecorder = new UserMetricsRecorder(system)
  }

  class UserMetricsRecorder(system: ActorSystem) extends MetricGroupRecorder {
    val precisionConfig = system.settings.config.getConfig("kamon.metrics.precision")
    val defaultHistogramPrecisionConfig = precisionConfig.getConfig("default-histogram-precision")
    val defaultMinMaxCounterPrecisionConfig = precisionConfig.getConfig("default-min-max-counter-precision")
    val defaultGaugePrecisionConfig = precisionConfig.getConfig("default-gauge-precision")

    val histograms = TrieMap[String, Histogram]()
    val counters = TrieMap[String, Counter]()
    val minMaxCounters = TrieMap[String, MinMaxCounter]()
    val gauges = TrieMap[String, Gauge]()

    def buildHistogram(name: String, precision: Histogram.Precision, highestTrackableValue: Long): Histogram =
      histograms.getOrElseUpdate(name, Histogram(highestTrackableValue, precision, Scale.Unit))

    def buildHistogram(name: String): Histogram =
      histograms.getOrElseUpdate(name, Histogram.fromConfig(defaultHistogramPrecisionConfig))

    def buildCounter(name: String): Counter =
      counters.getOrElseUpdate(name, Counter())

    def buildMinMaxCounter(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
      refreshInterval: FiniteDuration): MinMaxCounter = {
      minMaxCounters.getOrElseUpdate(name, MinMaxCounter(highestTrackableValue, precision, Scale.Unit, refreshInterval, system))
    }

    def buildMinMaxCounter(name: String): MinMaxCounter =
      minMaxCounters.getOrElseUpdate(name, MinMaxCounter.fromConfig(defaultMinMaxCounterPrecisionConfig, system))

    def buildGauge(name: String, precision: Histogram.Precision, highestTrackableValue: Long,
      refreshInterval: FiniteDuration, currentValueCollector: Gauge.CurrentValueCollector): Gauge =
      gauges.getOrElseUpdate(name, Gauge(precision, highestTrackableValue, Scale.Unit, refreshInterval, system)(currentValueCollector))

    def buildGauge(name: String)(currentValueCollector: Gauge.CurrentValueCollector): Gauge =
      gauges.getOrElseUpdate(name, Gauge.fromConfig(defaultGaugePrecisionConfig, system)(currentValueCollector))

    def collect(context: CollectionContext): UserMetricsSnapshot = {
      val histogramSnapshots = histograms.map {
        case (name, histogram) ⇒
          (UserHistogram(name), histogram.collect(context))
      } toMap

      val counterSnapshots = counters.map {
        case (name, counter) ⇒
          (UserCounter(name), counter.collect(context))
      } toMap

      val minMaxCounterSnapshots = minMaxCounters.map {
        case (name, minMaxCounter) ⇒
          (UserMinMaxCounter(name), minMaxCounter.collect(context))
      } toMap

      val gaugeSnapshots = gauges.map {
        case (name, gauge) ⇒
          (UserGauge(name), gauge.collect(context))
      } toMap

      UserMetricsSnapshot(histogramSnapshots, counterSnapshots, minMaxCounterSnapshots, gaugeSnapshots)
    }

    def cleanup: Unit = {}
  }

  case class UserHistogram(name: String) extends MetricIdentity
  case class UserCounter(name: String) extends MetricIdentity
  case class UserMinMaxCounter(name: String) extends MetricIdentity
  case class UserGauge(name: String) extends MetricIdentity

  case class UserMetricsSnapshot(histograms: Map[UserHistogram, Histogram.Snapshot],
    counters: Map[UserCounter, Counter.Snapshot],
    minMaxCounters: Map[UserMinMaxCounter, Histogram.Snapshot],
    gauges: Map[UserGauge, Histogram.Snapshot])
      extends MetricGroupSnapshot {

    type GroupSnapshotType = UserMetricsSnapshot

    def merge(that: UserMetricsSnapshot, context: CollectionContext): UserMetricsSnapshot =
      UserMetricsSnapshot(
        combineMaps(histograms, that.histograms)((l, r) ⇒ l.merge(r, context)),
        combineMaps(counters, that.counters)((l, r) ⇒ l.merge(r, context)),
        combineMaps(minMaxCounters, that.minMaxCounters)((l, r) ⇒ l.merge(r, context)),
        combineMaps(gauges, that.gauges)((l, r) ⇒ l.merge(r, context)))

    def metrics: Map[MetricIdentity, MetricSnapshot] = histograms ++ counters ++ minMaxCounters ++ gauges
  }

}
