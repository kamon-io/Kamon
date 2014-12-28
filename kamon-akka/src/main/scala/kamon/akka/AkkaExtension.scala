/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.akka

import akka.actor
import akka.actor._
import kamon._

class AkkaExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val config = system.settings.config.getConfig("kamon.akka")
  val askPatternTimeoutWarning = config.getString("ask-pattern-timeout-warning")
  val dispatcher = system.dispatchers.lookup(config.getString("dispatcher"))
}

object Akka extends ExtensionId[AkkaExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Akka
  def createExtension(system: ExtendedActorSystem): AkkaExtension = new AkkaExtension(system)
}