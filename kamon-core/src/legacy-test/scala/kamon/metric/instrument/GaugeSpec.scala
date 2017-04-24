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

package kamon.metric.instrument

import java.util.concurrent.atomic.AtomicLong
import kamon.Kamon
import kamon.metric.instrument.Histogram.DynamicRange
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class GaugeSpec extends BaseKamonSpec("gauge-spec") {

  "a Gauge" should {
    "automatically record the current value using the configured refresh-interval" in new GaugeFixture {
      val (numberOfValuesRecorded, gauge) = createGauge()
      Thread.sleep(1.second.toMillis)

      numberOfValuesRecorded.get() should be(10L +- 1L)
      gauge.cleanup
    }

    "stop automatically recording after a call to cleanup" in new GaugeFixture {
      val (numberOfValuesRecorded, gauge) = createGauge()
      Thread.sleep(1.second.toMillis)

      gauge.cleanup
      numberOfValuesRecorded.get() should be(10L +- 1L)
      Thread.sleep(1.second.toMillis)

      numberOfValuesRecorded.get() should be(10L +- 1L)
    }

    "produce a Histogram snapshot including all the recorded values" in new GaugeFixture {
      val (numberOfValuesRecorded, gauge) = createGauge()

      Thread.sleep(1.second.toMillis)
      gauge.cleanup
      val snapshot = gauge.collect(Kamon.metrics.buildDefaultCollectionContext)

      snapshot.numberOfMeasurements should be(10L +- 1L)
      snapshot.min should be(1)
      snapshot.max should be(10L +- 1L)
    }

    "not record the current value when doing a collection" in new GaugeFixture {
      val (numberOfValuesRecorded, gauge) = createGauge(10 seconds)

      val snapshot = gauge.collect(Kamon.metrics.buildDefaultCollectionContext)
      snapshot.numberOfMeasurements should be(0)
      numberOfValuesRecorded.get() should be(0)
    }
  }

  trait GaugeFixture {
    def createGauge(refreshInterval: FiniteDuration = 100 millis): (AtomicLong, Gauge) = {
      val recordedValuesCounter = new AtomicLong(0)
      val gauge = Gauge(DynamicRange(1, 100, 2), refreshInterval, Kamon.metrics.settings.refreshScheduler, {
        () ⇒ recordedValuesCounter.addAndGet(1)
      })

      (recordedValuesCounter, gauge)
    }

  }
}
