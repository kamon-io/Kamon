/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon

import scala.concurrent.duration._
import com.typesafe.config.Config

package object metrics {
  val OneHour = 1.hour.toNanos

  case class HdrConfiguration(highestTrackableValue: Long, significantValueDigits: Int)
  case object HdrConfiguration {
    def fromConfig(config: Config): HdrConfiguration = {
      HdrConfiguration(config.getLong("highest-trackable-value"), config.getInt("significant-value-digits"))
    }
  }
}
