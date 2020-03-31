/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import java.time.Duration

import com.newrelic.telemetry.Attributes
import kamon.metric.MetricSnapshot.Values
import kamon.metric.{MeasurementUnit, Metric, MetricSnapshot}
import org.scalatest.{Matchers, WordSpec}

class ConversionSupportSpec extends WordSpec with Matchers {

  "conversion support" should {
    "build attributes" in {
      val description = "some wonderful thing"
      val dimensionName = "percentage"
      val magnitudeName = "percentage"
      val scaleFactor = 1.0
      val expectedAttributes = new Attributes()
        .put("description", description)
        .put("dimensionName", dimensionName)
        .put("magnitudeName", magnitudeName)
        .put("scaleFactor", scaleFactor)
      val settings = Metric.Settings.ForValueInstrument(MeasurementUnit.percentage, Duration.ofMillis(556))
      val snapshot: Values[Long] = new MetricSnapshot.Values[Long]("", description, settings, Seq())
      val result = ConversionSupport.buildAttributes(snapshot)
      result shouldBe expectedAttributes
    }
  }
}
