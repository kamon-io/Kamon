package kamon.metric

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.UserMetrics.{ UserGauge, UserMinMaxCounter, UserCounter, UserHistogram }
import kamon.metric.instrument.Histogram
import kamon.metric.instrument.Histogram.MutableRecord
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration._

class UserMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  flush-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |  precision {
      |    default-histogram-precision {
      |      highest-trackable-value = 10000
      |      significant-value-digits = 2
      |    }
      |
      |    default-min-max-counter-precision {
      |      refresh-interval = 1 hour
      |      highest-trackable-value = 1000
      |      significant-value-digits = 2
      |    }
      |
      |    default-gauge-precision {
      |      refresh-interval = 1 hour
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
    """.stripMargin))

  "the UserMetrics extension" should {
    "allow registering a fully configured Histogram and get the same Histogram if registering again" in {
      val histogramA = Kamon(UserMetrics).registerHistogram("histogram-with-settings", Histogram.Precision.Normal, 10000L)
      val histogramB = Kamon(UserMetrics).registerHistogram("histogram-with-settings", Histogram.Precision.Normal, 10000L)

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "return the original Histogram when registering a fully configured Histogram for second time but with different settings" in {
      val histogramA = Kamon(UserMetrics).registerHistogram("histogram-with-settings", Histogram.Precision.Normal, 10000L)
      val histogramB = Kamon(UserMetrics).registerHistogram("histogram-with-settings", Histogram.Precision.Fine, 50000L)

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "allow registering a Histogram that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon(UserMetrics).registerHistogram("histogram-with-default-configuration")
    }

    "allow registering a Counter and get the same Counter if registering again" in {
      val counterA = Kamon(UserMetrics).registerCounter("counter")
      val counterB = Kamon(UserMetrics).registerCounter("counter")

      counterA shouldBe theSameInstanceAs(counterB)
    }

    "allow registering a fully configured MinMaxCounter and get the same MinMaxCounter if registering again" in {
      val minMaxCounterA = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-settings", Histogram.Precision.Normal, 1000L, 1 second)
      val minMaxCounterB = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-settings", Histogram.Precision.Normal, 1000L, 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "return the original MinMaxCounter when registering a fully configured MinMaxCounter for second time but with different settings" in {
      val minMaxCounterA = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-settings", Histogram.Precision.Normal, 1000L, 1 second)
      val minMaxCounterB = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-settings", Histogram.Precision.Fine, 5000L, 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "allow registering a MinMaxCounter that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-default-configuration")
    }

    "allow registering a fully configured Gauge and get the same Gauge if registering again" in {
      val gaugeA = Kamon(UserMetrics).registerGauge("gauge-with-settings", Histogram.Precision.Normal, 1000L, 1 second) {
        () ⇒ 1L
      }

      val gaugeB = Kamon(UserMetrics).registerGauge("gauge-with-settings", Histogram.Precision.Normal, 1000L, 1 second) {
        () ⇒ 1L
      }

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "return the original Gauge when registering a fully configured Gauge for second time but with different settings" in {
      val gaugeA = Kamon(UserMetrics).registerGauge("gauge-with-settings", Histogram.Precision.Normal, 1000L, 1 second) {
        () ⇒ 1L
      }

      val gaugeB = Kamon(UserMetrics).registerGauge("gauge-with-settings", Histogram.Precision.Fine, 5000L, 1 second) {
        () ⇒ 1L
      }

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "allow registering a Gauge that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon(UserMetrics).registerGauge("gauge-with-default-configuration") {
        () ⇒ 2L
      }
    }

    "generate a snapshot containing all the registered user metrics and reset all instruments" in {
      val context = Kamon(Metrics).buildDefaultCollectionContext
      val userMetricsRecorder = Kamon(Metrics).register(UserMetrics, UserMetrics.Factory).get

      val histogramWithSettings = Kamon(UserMetrics).registerHistogram("histogram-with-settings", Histogram.Precision.Normal, 10000L)
      val histogramWithDefaultConfiguration = Kamon(UserMetrics).registerHistogram("histogram-with-default-configuration")
      val counter = Kamon(UserMetrics).registerCounter("counter")
      val minMaxCounterWithSettings = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-with-settings", Histogram.Precision.Normal, 1000L, 1 second)
      val gauge = Kamon(UserMetrics).registerGauge("gauge-with-default-configuration") { () ⇒ 2L }

      // lets put some values on those metrics
      histogramWithSettings.record(10)
      histogramWithSettings.record(20, 100)
      histogramWithDefaultConfiguration.record(40)

      counter.increment()
      counter.increment(16)

      minMaxCounterWithSettings.increment(43)
      minMaxCounterWithSettings.decrement()

      gauge.record(15)

      val firstSnapshot = userMetricsRecorder.collect(context)

      firstSnapshot.histograms.size should be(2)
      firstSnapshot.histograms.keys should contain allOf (
        UserHistogram("histogram-with-settings"),
        UserHistogram("histogram-with-default-configuration"))

      firstSnapshot.histograms(UserHistogram("histogram-with-settings")).min shouldBe (10)
      firstSnapshot.histograms(UserHistogram("histogram-with-settings")).max shouldBe (20)
      firstSnapshot.histograms(UserHistogram("histogram-with-settings")).numberOfMeasurements should be(101)
      firstSnapshot.histograms(UserHistogram("histogram-with-settings")).recordsIterator.toStream should contain allOf (
        MutableRecord(10, 1),
        MutableRecord(20, 100))

      firstSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).min shouldBe (40)
      firstSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).max shouldBe (40)
      firstSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).numberOfMeasurements should be(1)
      firstSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).recordsIterator.toStream should contain only (
        MutableRecord(40, 1))

      firstSnapshot.counters(UserCounter("counter")).count should be(17)

      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).min shouldBe (0)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).max shouldBe (43)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).numberOfMeasurements should be(3)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min
        MutableRecord(42, 1), // current
        MutableRecord(43, 1)) // max

      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).min shouldBe (0)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).max shouldBe (0)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).numberOfMeasurements should be(3)
      firstSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).recordsIterator.toStream should contain only (
        MutableRecord(0, 3)) // min, max and current

      firstSnapshot.gauges(UserGauge("gauge-with-default-configuration")).min shouldBe (15)
      firstSnapshot.gauges(UserGauge("gauge-with-default-configuration")).max shouldBe (15)
      firstSnapshot.gauges(UserGauge("gauge-with-default-configuration")).numberOfMeasurements should be(1)
      firstSnapshot.gauges(UserGauge("gauge-with-default-configuration")).recordsIterator.toStream should contain only (
        MutableRecord(15, 1)) // only the manually recorded value

      val secondSnapshot = userMetricsRecorder.collect(context)

      secondSnapshot.histograms.size should be(2)
      secondSnapshot.histograms.keys should contain allOf (
        UserHistogram("histogram-with-settings"),
        UserHistogram("histogram-with-default-configuration"))

      secondSnapshot.histograms(UserHistogram("histogram-with-settings")).min shouldBe (0)
      secondSnapshot.histograms(UserHistogram("histogram-with-settings")).max shouldBe (0)
      secondSnapshot.histograms(UserHistogram("histogram-with-settings")).numberOfMeasurements should be(0)
      secondSnapshot.histograms(UserHistogram("histogram-with-settings")).recordsIterator.toStream shouldBe empty

      secondSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).min shouldBe (0)
      secondSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).max shouldBe (0)
      secondSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).numberOfMeasurements should be(0)
      secondSnapshot.histograms(UserHistogram("histogram-with-default-configuration")).recordsIterator.toStream shouldBe empty

      secondSnapshot.counters(UserCounter("counter")).count should be(0)

      secondSnapshot.minMaxCounters.size should be(2)
      secondSnapshot.minMaxCounters.keys should contain allOf (
        UserMinMaxCounter("min-max-counter-with-settings"),
        UserMinMaxCounter("min-max-counter-with-default-configuration"))

      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).min shouldBe (42)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).max shouldBe (42)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).numberOfMeasurements should be(3)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-settings")).recordsIterator.toStream should contain only (
        MutableRecord(42, 3)) // min, max and current

      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).min shouldBe (0)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).max shouldBe (0)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).numberOfMeasurements should be(3)
      secondSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-with-default-configuration")).recordsIterator.toStream should contain only (
        MutableRecord(0, 3)) // min, max and current

      secondSnapshot.gauges(UserGauge("gauge-with-default-configuration")).min shouldBe (0)
      secondSnapshot.gauges(UserGauge("gauge-with-default-configuration")).max shouldBe (0)
      secondSnapshot.gauges(UserGauge("gauge-with-default-configuration")).numberOfMeasurements should be(0)
      secondSnapshot.gauges(UserGauge("gauge-with-default-configuration")).recordsIterator shouldBe empty

    }

    "generate a snapshot that can be merged with another" in {
      val context = Kamon(Metrics).buildDefaultCollectionContext
      val userMetricsRecorder = Kamon(Metrics).register(UserMetrics, UserMetrics.Factory).get

      val histogram = Kamon(UserMetrics).registerHistogram("histogram-for-merge")
      val counter = Kamon(UserMetrics).registerCounter("counter-for-merge")
      val minMaxCounter = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-for-merge")
      val gauge = Kamon(UserMetrics).registerGauge("gauge-for-merge") { () ⇒ 10L }

      histogram.record(100)
      counter.increment(10)
      minMaxCounter.increment(50)
      minMaxCounter.decrement(10)
      gauge.record(50)

      val firstSnapshot = userMetricsRecorder.collect(context)

      val extraCounter = Kamon(UserMetrics).registerCounter("extra-counter")
      histogram.record(200)
      extraCounter.increment(20)
      minMaxCounter.increment(40)
      minMaxCounter.decrement(50)
      gauge.record(70)

      val secondSnapshot = userMetricsRecorder.collect(context)
      val mergedSnapshot = firstSnapshot.merge(secondSnapshot, context)

      mergedSnapshot.histograms.keys should contain(UserHistogram("histogram-for-merge"))

      mergedSnapshot.histograms(UserHistogram("histogram-for-merge")).min shouldBe (100)
      mergedSnapshot.histograms(UserHistogram("histogram-for-merge")).max shouldBe (200)
      mergedSnapshot.histograms(UserHistogram("histogram-for-merge")).numberOfMeasurements should be(2)
      mergedSnapshot.histograms(UserHistogram("histogram-for-merge")).recordsIterator.toStream should contain allOf (
        MutableRecord(100, 1),
        MutableRecord(200, 1))

      mergedSnapshot.counters(UserCounter("counter-for-merge")).count should be(10)
      mergedSnapshot.counters(UserCounter("extra-counter")).count should be(20)

      mergedSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-for-merge")).min shouldBe (0)
      mergedSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-for-merge")).max shouldBe (80)
      mergedSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-for-merge")).numberOfMeasurements should be(6)
      mergedSnapshot.minMaxCounters(UserMinMaxCounter("min-max-counter-for-merge")).recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min in first snapshot
        MutableRecord(30, 2), // min and current in second snapshot
        MutableRecord(40, 1), // current in first snapshot
        MutableRecord(50, 1), // max in first snapshot
        MutableRecord(80, 1)) // max in second snapshot

      mergedSnapshot.gauges(UserGauge("gauge-for-merge")).min shouldBe (50)
      mergedSnapshot.gauges(UserGauge("gauge-for-merge")).max shouldBe (70)
      mergedSnapshot.gauges(UserGauge("gauge-for-merge")).numberOfMeasurements should be(2)
      mergedSnapshot.gauges(UserGauge("gauge-for-merge")).recordsIterator.toStream should contain allOf (
        MutableRecord(50, 1),
        MutableRecord(70, 1))
    }
  }
}
