/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

import kamon.status.{Environment, Status}

/**
  * Exposes access to the Kamon's current status. The status information contains details about the internal state of
  * several Kamon components and is exposed for the sole purpose of troubleshooting and debugging issues that might be
  * related to Kamon.
  *
  * The Status APIs might change between minor versions.
  */
trait CurrentStatus {
  self: ModuleManagement with Configuration with Utilities with Metrics with Tracing with ContextStorage =>
  @volatile private var _environment = Environment.from(self.config())
  private val _status = new Status(self._moduleRegistry, self._metricRegistry, self)

  onReconfigure(newConfig => _environment = Environment.from(newConfig))

  /**
    * Returns the current enviroment instance constructed by Kamon using the "kamon.environment" settings.
    */
  def environment: Environment =
    _environment

  /**
    * Returns an accessor to Kamon's current status. The current status information is split into four main sections:
    *   - Settings: which include the Kamon version, environment and configuration being used.
    *   - Module Registry: Lists all modules that have been detected on the classpath and their current state.
    *   - Metric Registry: Lists all metrics currently registered in Kamon and all instruments belonging to them.
    *   - Instrumentation: Lists all instrumentation modules that have been detected and their current state.
    *
    * All information exposed by the Status API represents an immutable snapshot of the state at the moment the status
    * was requested.
    */
  def status(): kamon.status.Status =
    _status
}
