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

import _root_.akka.actor._
import com.typesafe.config.Config
import kamon._
import kamon.metric.Entity

import _root_.scala.util.{ Failure, Success, Try }

object AkkaExtension {
  val config = Kamon.config.getConfig("kamon.akka")

  val askPatternTimeoutWarning = AskPatternTimeoutWarningSettings.fromConfig(config)

  val actorEntityFactory: ActorEntityFactory = if (config.hasPath("actor-entity-factory")) {
    Try(Class.forName(config.getString("actor-entity-factory")).newInstance()) match {
      case Success(f: ActorEntityFactory) ⇒ f
      case Success(_) ⇒
        throw new IllegalArgumentException("actor-entity-factory must extend trait kamon.akka.ActorEntityFactory")
      case Failure(reason) ⇒
        throw new RuntimeException(s"Failed to create an instance of entity factory defined by actor-entity-factory")
    }
  } else {
    DefaultActorEntityFactory
  }
}

trait ActorEntityFactory {
  def build(system: ActorSystem, ref: ActorRef): Entity
}

object DefaultActorEntityFactory extends ActorEntityFactory {
  override def build(system: ActorSystem, ref: ActorRef): Entity = {
    val pathString = ref.path.elements.mkString("/")
    Entity(system.name + "/" + pathString, ActorMetrics.category)
  }
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

