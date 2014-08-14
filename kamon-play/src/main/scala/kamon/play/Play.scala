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

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.Logging
import kamon.Kamon
import kamon.http.HttpServerMetrics
import kamon.metric.Metrics

object Play extends ExtensionId[PlayExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Play
  override def createExtension(system: ExtendedActorSystem): PlayExtension = new PlayExtension(system)
}

class PlayExtension(private val system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[PlayExtension])
  log.info(s"Starting the Kamon(Play) extension")

  private val config = system.settings.config.getConfig("kamon.play")

  val httpServerMetrics = Kamon(Metrics)(system).register(HttpServerMetrics, HttpServerMetrics.Factory).get
  val defaultDispatcher = system.dispatchers.lookup(config.getString("dispatcher"))
  val includeTraceToken: Boolean = config.getBoolean("include-trace-token-header")
  val traceTokenHeaderName: String = config.getString("trace-token-header-name")
}

