/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.util

import com.typesafe.config.Config
import kamon.metric.instrument.{ Memory, Time }
import kamon.util.ConfigTools._

object NeedToScale {
  val TimeUnits = "time-units"
  val MemoryUnits = "memory-units"

  def unapply(config: Config): Option[(Option[Time], Option[Memory])] = {
    val scaleTimeTo: Option[Time] =
      if (config.hasPath(TimeUnits)) Some(config.time(TimeUnits)) else None

    val scaleMemoryTo: Option[Memory] =
      if (config.hasPath(MemoryUnits)) Some(config.memory(MemoryUnits)) else None
    if (scaleTimeTo.isDefined || scaleMemoryTo.isDefined) Some(scaleTimeTo -> scaleMemoryTo)
    else None
  }
}

