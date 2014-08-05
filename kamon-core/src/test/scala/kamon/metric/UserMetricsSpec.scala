package kamon.metric

import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.UserMetrics._
import kamon.metric.instrument.{ Histogram, Counter, MinMaxCounter, Gauge }
import kamon.metric.instrument.Histogram.MutableRecord
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration._

class UserMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit def self = testActor
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  default-collection-context-buffer-size = 10
      |
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

    "allow un-registering user metrics" in {
      val metricsExtension = Kamon(Metrics)
      Kamon(UserMetrics).registerCounter("counter-for-remove")
      Kamon(UserMetrics).registerHistogram("histogram-for-remove")
      Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-for-remove")
      Kamon(UserMetrics).registerGauge("gauge-for-remove") { () ⇒ 2L }

      metricsExtension.storage.keys should contain(UserCounter("counter-for-remove"))
      metricsExtension.storage.keys should contain(UserHistogram("histogram-for-remove"))
      metricsExtension.storage.keys should contain(UserMinMaxCounter("min-max-counter-for-remove"))
      metricsExtension.storage.keys should contain(UserGauge("gauge-for-remove"))

      Kamon(UserMetrics).removeCounter("counter-for-remove")
      Kamon(UserMetrics).removeHistogram("histogram-for-remove")
      Kamon(UserMetrics).removeMinMaxCounter("min-max-counter-for-remove")
      Kamon(UserMetrics).removeGauge("gauge-for-remove")

      metricsExtension.storage.keys should not contain (UserCounter("counter-for-remove"))
      metricsExtension.storage.keys should not contain (UserHistogram("histogram-for-remove"))
      metricsExtension.storage.keys should not contain (UserMinMaxCounter("min-max-counter-for-remove"))
      metricsExtension.storage.keys should not contain (UserGauge("gauge-for-remove"))
    }

    "include all the registered metrics in the a tick snapshot and reset all recorders" in {
      Kamon(Metrics).subscribe(UserHistograms, "*", testActor, permanently = true)
      Kamon(Metrics).subscribe(UserCounters, "*", testActor, permanently = true)
      Kamon(Metrics).subscribe(UserMinMaxCounters, "*", testActor, permanently = true)
      Kamon(Metrics).subscribe(UserGauges, "*", testActor, permanently = true)

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

      Kamon(Metrics).subscriptions ! Subscriptions.FlushMetrics
      val firstSnapshot = expectMsgType[TickMetricSnapshot].metrics

      firstSnapshot.keys should contain allOf (
        UserHistogram("histogram-with-settings"),
        UserHistogram("histogram-with-default-configuration"))

      firstSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (10)
      firstSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (20)
      firstSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(101)
      firstSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain allOf (
        MutableRecord(10, 1),
        MutableRecord(20, 100))

      firstSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (40)
      firstSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (40)
      firstSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(1)
      firstSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain only (
        MutableRecord(40, 1))

      firstSnapshot(UserCounter("counter")).metrics(Count).asInstanceOf[Counter.Snapshot].count should be(17)

      firstSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (43)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(3)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min
        MutableRecord(42, 1), // current
        MutableRecord(43, 1)) // max

      firstSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (0)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(3)
      firstSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain only (
        MutableRecord(0, 3)) // min, max and current

      firstSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (15)
      firstSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (15)
      firstSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(1)
      firstSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain only (
        MutableRecord(15, 1)) // only the manually recorded value

      Kamon(Metrics).subscriptions ! Subscriptions.FlushMetrics
      val secondSnapshot = expectMsgType[TickMetricSnapshot].metrics

      secondSnapshot.keys should contain allOf (
        UserHistogram("histogram-with-settings"),
        UserHistogram("histogram-with-default-configuration"))

      secondSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      secondSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (0)
      secondSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(0)
      secondSnapshot(UserHistogram("histogram-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream shouldBe empty

      secondSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      secondSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (0)
      secondSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(0)
      secondSnapshot(UserHistogram("histogram-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream shouldBe empty

      secondSnapshot(UserCounter("counter")).metrics(Count).asInstanceOf[Counter.Snapshot].count should be(0)

      secondSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (42)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (42)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(3)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-settings")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain only (
        MutableRecord(42, 3)) // max

      secondSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (0)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(3)
      secondSnapshot(UserMinMaxCounter("min-max-counter-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain only (
        MutableRecord(0, 3)) // min, max and current

      secondSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      secondSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (0)
      secondSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(0)
      secondSnapshot(UserGauge("gauge-with-default-configuration")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream shouldBe empty

      Kamon(Metrics).unsubscribe(testActor)
    }

    "generate a snapshot that can be merged with another" in {
      val buffer = system.actorOf(TickMetricSnapshotBuffer.props(1 hours, testActor))
      Kamon(Metrics).subscribe(UserHistograms, "*", buffer, permanently = true)
      Kamon(Metrics).subscribe(UserCounters, "*", buffer, permanently = true)
      Kamon(Metrics).subscribe(UserMinMaxCounters, "*", buffer, permanently = true)
      Kamon(Metrics).subscribe(UserGauges, "*", buffer, permanently = true)

      val histogram = Kamon(UserMetrics).registerHistogram("histogram-for-merge")
      val counter = Kamon(UserMetrics).registerCounter("counter-for-merge")
      val minMaxCounter = Kamon(UserMetrics).registerMinMaxCounter("min-max-counter-for-merge")
      val gauge = Kamon(UserMetrics).registerGauge("gauge-for-merge") { () ⇒ 10L }

      histogram.record(100)
      counter.increment(10)
      minMaxCounter.increment(50)
      minMaxCounter.decrement(10)
      gauge.record(50)

      Kamon(Metrics).subscriptions ! Subscriptions.FlushMetrics
      Thread.sleep(2000) // Make sure that the snapshots are taken before proceeding

      val extraCounter = Kamon(UserMetrics).registerCounter("extra-counter")
      histogram.record(200)
      extraCounter.increment(20)
      minMaxCounter.increment(40)
      minMaxCounter.decrement(50)
      gauge.record(70)

      Kamon(Metrics).subscriptions ! Subscriptions.FlushMetrics
      Thread.sleep(2000) // Make sure that the metrics are buffered.
      buffer ! TickMetricSnapshotBuffer.FlushBuffer
      val snapshot = expectMsgType[TickMetricSnapshot].metrics

      snapshot.keys should contain(UserHistogram("histogram-for-merge"))

      snapshot(UserHistogram("histogram-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (100)
      snapshot(UserHistogram("histogram-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (200)
      snapshot(UserHistogram("histogram-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(2)
      snapshot(UserHistogram("histogram-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain allOf (
        MutableRecord(100, 1),
        MutableRecord(200, 1))

      snapshot(UserCounter("counter-for-merge")).metrics(Count).asInstanceOf[Counter.Snapshot].count should be(10)
      snapshot(UserCounter("extra-counter")).metrics(Count).asInstanceOf[Counter.Snapshot].count should be(20)

      snapshot(UserMinMaxCounter("min-max-counter-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (0)
      snapshot(UserMinMaxCounter("min-max-counter-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (80)
      snapshot(UserMinMaxCounter("min-max-counter-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(6)
      snapshot(UserMinMaxCounter("min-max-counter-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min in first snapshot
        MutableRecord(30, 2), // min and current in second snapshot
        MutableRecord(40, 1), // current in first snapshot
        MutableRecord(50, 1), // max in first snapshot
        MutableRecord(80, 1)) // max in second snapshot

      snapshot(UserGauge("gauge-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].min shouldBe (50)
      snapshot(UserGauge("gauge-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].max shouldBe (70)
      snapshot(UserGauge("gauge-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].numberOfMeasurements should be(2)
      snapshot(UserGauge("gauge-for-merge")).metrics(RecordedValues).asInstanceOf[Histogram.Snapshot].recordsIterator.toStream should contain allOf (
        MutableRecord(50, 1),
        MutableRecord(70, 1))

      Kamon(Metrics).unsubscribe(testActor)
    }
  }
}
