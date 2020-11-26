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

package kamon

import kamon.metric._

/**
  * Exposes all metric building APIs using a built-in, globally shared metric registry.
  */
trait Metrics extends MetricBuilding { self: Configuration with Utilities =>
  protected val _metricRegistry = new MetricRegistry(self.config(), self.scheduler(), self.clock())
  onReconfigure(newConfig => _metricRegistry.reconfigure(newConfig))

  /**
    * Metric registry from which all metric-building APIs will draw instances. For more details on the entire set of
    * exposes APIs please refer to [[kamon.metric.MetricBuilding.]]
    */
  protected def registry(): MetricRegistry =
    _metricRegistry

}
