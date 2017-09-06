/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.system.jmx

import kamon.Kamon
import kamon.system.SystemMetrics
import org.slf4j.Logger

abstract class JmxMetricBuilder(metricName: String) {
  private val filterName = SystemMetrics.FilterName
  private val logger = SystemMetrics.logger

  def register(): Option[JmxMetric] = {
    if (Kamon.filter(filterName, metricName))
      Some(build(s"$filterName.$metricName", logger))
    else
      None
  }

  def build(metricPrefix: String, logger: Logger): JmxMetric
}

trait JmxMetric {
  def update(): Unit
}

