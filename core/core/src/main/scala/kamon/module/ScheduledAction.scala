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

package kamon.module

/**
  * Modules implementing this will have a receive a scheduled call to their `run` method in a fixed interval. The
  * call interval is controlled by the `kamon.modules.{module-name}.interval` setting, or passed in programmatically
  * when adding a new scheduled action via `Kamon.addScheduledAction(...)`
  */
trait ScheduledAction extends Module {
  def run(): Unit
}
