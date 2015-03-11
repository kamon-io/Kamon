/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.metric

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class SimpleMetricsSpec extends BaseKamonSpec("simple-metrics-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 10
        |}
      """.stripMargin)

  "the SimpleMetrics extension" should {

    "allow registering a fully configured Histogram and get the same Histogram if registering again" in {
      val histogramA = Kamon.metrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))
      val histogramB = Kamon.metrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "return the original Histogram when registering a fully configured Histogram for second time but with different settings" in {
      val histogramA = Kamon.metrics.histogram("histogram-with-settings", DynamicRange(1, 10000, 2))
      val histogramB = Kamon.metrics.histogram("histogram-with-settings", DynamicRange(1, 50000, 2))

      histogramA shouldBe theSameInstanceAs(histogramB)
    }

    "allow registering a Histogram that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon.metrics.histogram("histogram-with-default-configuration")
    }

    "allow registering a Counter and get the same Counter if registering again" in {
      val counterA = Kamon.metrics.counter("counter")
      val counterB = Kamon.metrics.counter("counter")

      counterA shouldBe theSameInstanceAs(counterB)
    }

    "allow registering a fully configured MinMaxCounter and get the same MinMaxCounter if registering again" in {
      val minMaxCounterA = Kamon.metrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)
      val minMaxCounterB = Kamon.metrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "return the original MinMaxCounter when registering a fully configured MinMaxCounter for second time but with different settings" in {
      val minMaxCounterA = Kamon.metrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 10000, 2), 1 second)
      val minMaxCounterB = Kamon.metrics.minMaxCounter("min-max-counter-with-settings", DynamicRange(1, 50000, 2), 1 second)

      minMaxCounterA shouldBe theSameInstanceAs(minMaxCounterB)
    }

    "allow registering a MinMaxCounter that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon.metrics.minMaxCounter("min-max-counter-with-default-configuration")
    }

    "allow registering a fully configured Gauge and get the same Gauge if registering again" in {
      val gaugeA = Kamon.metrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second)(1L)
      val gaugeB = Kamon.metrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second)(1L)

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "return the original Gauge when registering a fully configured Gauge for second time but with different settings" in {
      val gaugeA = Kamon.metrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second)(1L)
      val gaugeB = Kamon.metrics.gauge("gauge-with-settings", DynamicRange(1, 10000, 2), 1 second)(1L)

      gaugeA shouldBe theSameInstanceAs(gaugeB)
    }

    "allow registering a Gauge that takes the default configuration from the kamon.metrics.precision settings" in {
      Kamon.metrics.gauge("gauge-with-default-configuration")(2L)
    }

    "allow un-registering user metrics" in {
      val counter = Kamon.metrics.counter("counter-for-remove")
      val histogram = Kamon.metrics.histogram("histogram-for-remove")
      val minMaxCounter = Kamon.metrics.minMaxCounter("min-max-counter-for-remove")
      val gauge = Kamon.metrics.gauge("gauge-for-remove")(2L)

      Kamon.metrics.removeCounter("counter-for-remove")
      Kamon.metrics.removeHistogram("histogram-for-remove")
      Kamon.metrics.removeMinMaxCounter("min-max-counter-for-remove")
      Kamon.metrics.removeGauge("gauge-for-remove")

      counter should not be (theSameInstanceAs(Kamon.metrics.counter("counter-for-remove")))
      histogram should not be (theSameInstanceAs(Kamon.metrics.histogram("histogram-for-remove")))
      minMaxCounter should not be (theSameInstanceAs(Kamon.metrics.minMaxCounter("min-max-counter-for-remove")))
      gauge should not be (theSameInstanceAs(Kamon.metrics.gauge("gauge-for-remove")(2L)))
    }
  }
}
