/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.play

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionIdProvider, ExtensionId }
import kamon.Kamon

object Play extends ExtensionId[PlayExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Play
  override def createExtension(system: ExtendedActorSystem): PlayExtension = new PlayExtension(system)
}

class PlayExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  publishInfoMessage(system, "Play Extension Loaded!!")

  private val config = system.settings.config.getConfig("kamon.play")

  val defaultDispatcher = system.dispatchers.lookup(config.getString("dispatcher"))
  val includeTraceToken: Boolean = config.getBoolean("include-trace-token-header")
  val traceTokenHeaderName: String = config.getString("trace-token-header-name")
}

