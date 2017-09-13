/* =========================================================================================
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

package kamon.jdbc.instrumentation.mixin

import kamon.jdbc.Metrics

trait HasConnectionPoolMetrics {
  def connectionPoolMetrics: Metrics.ConnectionPoolMetrics
  def setConnectionPoolMetrics(cpm: Metrics.ConnectionPoolMetrics): Unit
}

class HasConnectionPoolMetricsMixin extends HasConnectionPoolMetrics {
  @volatile private var tracker: Metrics.ConnectionPoolMetrics = _

  override def connectionPoolMetrics: Metrics.ConnectionPoolMetrics = this.tracker
  override def setConnectionPoolMetrics(cpm: Metrics.ConnectionPoolMetrics): Unit = this.tracker = cpm
}

