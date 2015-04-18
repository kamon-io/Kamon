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

import _root_.akka.actor
import _root_.akka.actor._
import _root_.akka.event.Logging
import com.typesafe.config.Config
import kamon._

class AkkaExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[AkkaExtension])
  val config = system.settings.config.getConfig("kamon.akka")

  val askPatternTimeoutWarning = AskPatternTimeoutWarningSettings.fromConfig(config)
}

object Akka extends ExtensionId[AkkaExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Akka
  def createExtension(system: ExtendedActorSystem): AkkaExtension = new AkkaExtension(system)
}

sealed trait AskPatternTimeoutWarningSetting
object AskPatternTimeoutWarningSettings {
  case object Off extends AskPatternTimeoutWarningSetting
  case object Lightweight extends AskPatternTimeoutWarningSetting
  case object Heavyweight extends AskPatternTimeoutWarningSetting

  def fromConfig(config: Config): AskPatternTimeoutWarningSetting = config.getString("ask-pattern-timeout-warning") match {
    case "off"         ⇒ Off
    case "lightweight" ⇒ Lightweight
    case "heavyweight" ⇒ Heavyweight
    case other         ⇒ sys.error(s"Unrecognized option [$other] for the kamon.akka.ask-pattern-timeout-warning config.")
  }
}

