/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import kamon.metric.MetricSnapshot.Values

object ConversionSupport {

  def buildAttributes(metric: Values[_]): Attributes = {
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
