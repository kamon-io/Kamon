package kamon.metric

import com.typesafe.config.ConfigFactory
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class UserMetricsSpec extends BaseKamonSpec("user-metrics-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 10
        |}
      """.stripMargin)

  "the UserMetrics extension" should {

    "allow registering a fully configured Histogram and get the same Histogram if registering again" in {
      val histogramA = kamon.userMetrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))
      val histogramB = kamon.userMetrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "return the original Histogram when registering a fully configured Histogram for second time but with different settings" in {
      val histogramA = kamon.userMetrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))
      val histogramB = kamon.userMetrics.histogram("histogram-with-settings", DynamicRange(1, 50000, 2))

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "allow registering a Histogram that takes the default configuration from the kamon.metrics.precision settings" in {
      kamon.userMetrics.histogram("histogram-with-default-configuration")
    }

    "allow registering a Counter and get the same Counter if registering again" in {
      val counterA = kamon.userMetrics.counter("counter")
      val counterB = kamon.userMetrics.counter("counter")

      counterA shouldBe theSameInstanceAs(counterB)
    }

    "allow registering a fully configured MinMaxCounter and get the same MinMaxCounter if registering again" in {
      val minMaxCounterA = kamon.userMetrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)
      val minMaxCounterB = kamon.userMetrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "return the original MinMaxCounter when registering a fully configured MinMaxCounter for second time but with different settings" in {
      val minMaxCounterA = kamon.userMetrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)
      val minMaxCounterB = kamon.userMetrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 50000, 2), 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "allow registering a MinMaxCounter that takes the default configuration from the kamon.metrics.precision settings" in {
      kamon.userMetrics.minMaxCounter("min-max-counter-with-default-configuration")
    }

    "allow registering a fully configured Gauge and get the same Gauge if registering again" in {
      val gaugeA = kamon.userMetrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second, {
        () ⇒ 1L
      })

      val gaugeB = kamon.userMetrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second, {
        () ⇒ 1L
      })

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "return the original Gauge when registering a fully configured Gauge for second time but with different settings" in {
      val gaugeA = kamon.userMetrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second, {
        () ⇒ 1L
      })

      val gaugeB = kamon.userMetrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second, {
        () ⇒ 1L
      })

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "allow registering a Gauge that takes the default configuration from the kamon.metrics.precision settings" in {
      kamon.userMetrics.gauge("gauge-with-default-configuration", {
        () ⇒ 2L
      })
    }

    "allow un-registering user metrics" in {
      val counter = kamon.userMetrics.counter("counter-for-remove")
      val histogram = kamon.userMetrics.histogram("histogram-for-remove")
      val minMaxCounter = kamon.userMetrics.minMaxCounter("min-max-counter-for-remove")
      val gauge = kamon.userMetrics.gauge("gauge-for-remove", { () ⇒ 2L })

      kamon.userMetrics.removeCounter("counter-for-remove")
      kamon.userMetrics.removeHistogram("histogram-for-remove")
      kamon.userMetrics.removeMinMaxCounter("min-max-counter-for-remove")
      kamon.userMetrics.removeGauge("gauge-for-remove")

      counter should not be (theSameInstanceAs(kamon.userMetrics.counter("counter-for-remove")))
      histogram should not be (theSameInstanceAs(kamon.userMetrics.histogram("histogram-for-remove")))
      minMaxCounter should not be (theSameInstanceAs(kamon.userMetrics.minMaxCounter("min-max-counter-for-remove")))
      gauge should not be (theSameInstanceAs(kamon.userMetrics.gauge("gauge-for-remove", { () ⇒ 2L })))
    }
  }
}
