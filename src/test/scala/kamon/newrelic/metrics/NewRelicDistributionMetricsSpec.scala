/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.{Gauge, Summary}
import kamon.metric.MetricSnapshot.Distributions
import org.scalatest.{Matchers, WordSpec}

class NewRelicDistributionMetricsSpec extends WordSpec with Matchers {

  "distribution converter" should {
    "convert a distribution" in {
      val distributions: Distributions = TestMetricHelper.buildHistogramDistribution
      val summaryAttributes = new Attributes()
        .put("magnitude.name", "eimer")
        .put("magnitude.scaleFactor", 603.3d)
        .put("lowestDiscernibleValue", 1L)
        .put("highestTrackableValue", 3600000000000L)
        .put("significantValueDigits", 2)
        .put("twelve", "bishop")
        .put("dimension", "information")
        .put("sourceMetricType", "mountain")
      val summary = new Summary("trev.summary", 44, 101.0, 13.0, 17.0, TestMetricHelper.start, TestMetricHelper.end, summaryAttributes)
      val gaugeAttributes = summaryAttributes.copy().put("percentile", 90.0d)
      val gauge = new Gauge("trev.percentiles", 2.0, TestMetricHelper.end, gaugeAttributes)
      val expectedMetrics = Seq(gauge, summary)
      val result = NewRelicDistributionMetrics(TestMetricHelper.start, TestMetricHelper.end, distributions, "mountain")
      result shouldBe expectedMetrics
    }
  }

}
