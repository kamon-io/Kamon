/*
 *  Copyright 2019 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import kamon.metric.MetricSnapshot.Values

object ConversionSupport {

  def buildAttributes(metric: Values[_]) : Attributes = {
    val dimensionName = metric.settings.unit.dimension.name
    val magnitudeName = metric.settings.unit.magnitude.name
    val scaleFactor = metric.settings.unit.magnitude.scaleFactor
    new Attributes()
      .put("description", metric.description)
      .put("dimensionName", dimensionName)
      .put("magnitudeName", magnitudeName)
      .put("scaleFactor", scaleFactor)
  }
}
