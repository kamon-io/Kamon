/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.Gauge
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NewRelicGaugesSpec extends AnyWordSpec with Matchers {

  "gauge converter" should {
    "convert a gauge" in {
      val timestamp: Long = System.currentTimeMillis()
      val kamonGauge = TestMetricHelper.buildGauge
      val attributes = new Attributes()
        .put("description", "another one")
        .put("magnitudeName", "finch")
        .put("dimensionName", "information")
        .put("scaleFactor", 11.0)
        .put("foo", "bar")
        .put("sourceMetricType", "gauge")
      val expectedGauge = new Gauge("shirley", 15.6d, timestamp, attributes)
      val result = NewRelicGauges(timestamp, kamonGauge)
      result shouldBe Seq(expectedGauge)
    }
  }

}
