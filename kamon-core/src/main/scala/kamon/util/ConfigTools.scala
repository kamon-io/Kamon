/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

import kamon.metric.instrument.{ Memory, Time }

object ConfigTools {
  implicit class Syntax(val config: Config) extends AnyVal {
    // We are using the deprecated .getNanoseconds option to keep Kamon source code compatible with
    // versions of Akka using older typesafe-config versions.

    def getFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(config.getDuration(path, TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)

    def firstLevelKeys: Set[String] = {
      import scala.collection.JavaConverters._

      config.entrySet().asScala.map {
        case entry ⇒ entry.getKey.takeWhile(_ != '.')
      } toSet
    }

    def time(path: String): Time = Time(config.getString(path))

    def memory(path: String): Memory = Memory(config.getString(path))
  }

}
