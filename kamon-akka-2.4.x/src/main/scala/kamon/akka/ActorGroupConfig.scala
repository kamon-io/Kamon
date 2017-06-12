/* =========================================================================================
 * Copyright © 2017 the kamon project <http://kamon.io/>
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

package kamon.akka

import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions, ConfigResolveOptions }

import kamon.metric.EntityFilter
import kamon.util.{ GlobPathFilter, RegexPathFilter }

object ActorGroupConfig {
  private val defaultConfig = ConfigFactory.load(this.getClass.getClassLoader, ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setAllowUnresolved(true))

  implicit class Syntax(val config: Config) extends AnyVal {
    def firstLevelKeys: Set[String] = {
      import scala.collection.JavaConverters._

      config.entrySet().asScala.map {
        case entry ⇒ entry.getKey.takeWhile(_ != '.')
      } toSet
    }
  }

  private val groupFilters = {
    val groupPath = "kamon.metric.actor-group"
    if(defaultConfig.hasPath(groupPath)) {
      val cfg = defaultConfig.getConfig(groupPath)
      createFilters(cfg, cfg.firstLevelKeys)
    } else {
      Map.empty
    }
  }

  private def createFilters(cfg: Config, categories: Set[String]): Map[String, EntityFilter] = {
    import scala.collection.JavaConverters._
    categories map { category: String ⇒
      val asRegex = if (cfg.hasPath(s"$category.asRegex")) cfg.getBoolean(s"$category.asRegex") else false
      val includes = cfg.getStringList(s"$category.includes").asScala.map(inc ⇒
        if (asRegex) RegexPathFilter(inc) else new GlobPathFilter(inc)).toList
      val excludes = cfg.getStringList(s"$category.excludes").asScala.map(exc ⇒
        if (asRegex) RegexPathFilter(exc) else new GlobPathFilter(exc)).toList

      (category, EntityFilter(includes, excludes))
    } toMap
  }

  def actorShouldBeTrackedUnderGroups(entityName: String): List[String] = {
    val iterable = for((groupName, filter) <- groupFilters if filter.accept(entityName)) yield groupName
    iterable.toList
  }

  def groupNames: Set[String] = groupFilters.keys.toSet
}
