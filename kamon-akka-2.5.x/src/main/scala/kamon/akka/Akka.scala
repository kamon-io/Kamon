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

import com.typesafe.config.Config
import kamon.Configuration.OnReconfigureHook
import kamon.Kamon
import kamon.akka.Akka.ActorTracingFilterName
import kamon.akka.AskPatternTimeoutWarningSettings.Off
import kamon.util.Filter

import scala.collection.JavaConverters._

object Akka {
  val ActorFilterName = "akka.tracked-actor"
  val RouterFilterName = "akka.tracked-router"
  val DispatcherFilterName = "akka.tracked-dispatcher"
  val ActorTracingFilterName = "akka.traced-actor"

  @volatile var askPatternTimeoutWarning: AskPatternTimeoutWarningSetting = Off
  @volatile private var _actorGroups = Map.empty[String, Filter]
  @volatile private var _actorFilters = Map.empty[String, Filter]
  @volatile private var _configProvidedActorGroups = Map.empty[String, Filter]
  @volatile private var _codeProvidedActorGroups = Map.empty[String, Filter]

  loadConfiguration(Kamon.config())

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      Akka.loadConfiguration(newConfig)
  })

  private def loadConfiguration(config: Config): Unit = synchronized {
    val akkaConfig = config.getConfig("kamon.akka")
    val groupsConfig = akkaConfig.getConfig("filters.groups")

    askPatternTimeoutWarning = AskPatternTimeoutWarningSettings.fromConfig(akkaConfig)

    _configProvidedActorGroups = groupsConfig.root.entrySet().asScala.map(entry => {
      val groupName = entry.getKey
      groupName -> Filter.from(groupsConfig.getConfig(groupName))
    }).toMap

    _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups

    _actorFilters = loadFilters(akkaConfig)
  }

  private def loadFilters(config: Config): Map[String, Filter] = synchronized {
    val filterConfig = config.getConfig("filters")
    Map(
      ActorFilterName         -> Filter.from(filterConfig.getConfig("actors.track")),
      ActorTracingFilterName  -> Filter.from(filterConfig.getConfig("actors.trace")),
      RouterFilterName        -> Filter.from(filterConfig.getConfig("routers")),
      DispatcherFilterName    -> Filter.from(filterConfig.getConfig("dispatchers")),
    )
  }

  def filters: Map[String, Filter] = _actorFilters

  def actorGroups(path: String): Seq[String] = {
    _actorGroups.filter { case (_, v) => v.accept(path) }.keys.toSeq
  }

  def addActorGroup(groupName: String, filter: Filter): Boolean = synchronized {
    if(_codeProvidedActorGroups.get(groupName).isEmpty) {
      _codeProvidedActorGroups = _codeProvidedActorGroups + (groupName -> filter)
      _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups
      true
    } else false
  }

  def removeActorGroup(groupName: String): Unit = synchronized {
    _codeProvidedActorGroups = _codeProvidedActorGroups - groupName
    _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups
  }
}

sealed trait AskPatternTimeoutWarningSetting
object AskPatternTimeoutWarningSettings {
  case object Off extends AskPatternTimeoutWarningSetting
  case object Lightweight extends AskPatternTimeoutWarningSetting
  case object Heavyweight extends AskPatternTimeoutWarningSetting

  def fromConfig(config: Config): AskPatternTimeoutWarningSetting =
    config.getString("ask-pattern-timeout-warning") match {
      case "off"         ⇒ Off
      case "lightweight" ⇒ Lightweight
      case "heavyweight" ⇒ Heavyweight
      case other         ⇒ sys.error(s"Unrecognized option [$other] for the kamon.akka.ask-pattern-timeout-warning config.")
    }
}