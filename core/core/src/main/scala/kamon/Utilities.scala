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

import com.typesafe.config.Config
import kamon.util.{Clock, Filter}

import scala.collection.concurrent.TrieMap

/**
  * Base utilities used by other Kamon components.
  */
trait Utilities { self: Configuration =>
  private val _clock = new Clock.Anchored()
  private val _filters = TrieMap.empty[String, Filter]

  reconfigureUtilities(self.config())
  self.onReconfigure(newConfig => reconfigureUtilities(newConfig))

  /**
    * Creates a new composite Filter by looking up the provided key on Kamon's configuration. All inputs matching any of
    * the include filters and none of the exclude filters will be accepted. The configuration is expected to have the
    * following structure:
    *
    * config {
    *   includes = [ "some/pattern", "regex:some[0-9]" ]
    *   excludes = [ ]
    * }
    *
    * By default, the patterns are treated as Glob patterns but users can explicitly configure the pattern type by
    * prefixing the pattern with either "glob:" or "regex:". If any of the elements are missing they will be considered
    * empty.
    */
  def filter(configKey: String): Filter =
    _filters.getOrElseUpdate(configKey, Filter.from(configKey))

  /**
    * Kamon's Clock implementation.
    */
  def clock(): Clock =
    _clock

  private def reconfigureUtilities(config: Config): Unit = {
    _filters.clear()
  }
}
