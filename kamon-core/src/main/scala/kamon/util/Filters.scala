/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
package util

import java.util.regex.Pattern

import com.typesafe.config.{Config, ConfigUtil}

object Filters {
  def fromConfig(config: Config): Filters = {
    val filtersConfig = config.getConfig("kamon.util.filters")

    val perMetricFilter = filtersConfig.topLevelKeys map { filterName: String ⇒
      val includes = readFilters(filtersConfig, filterName, "includes")
      val excludes = readFilters(filtersConfig, filterName, "excludes")

      (filterName, new IncludeExcludeMatcher(includes, excludes))
    } toMap

    new Filters(perMetricFilter)
  }

  private def readFilters(filtersConfig: Config, name: String, key: String): Seq[Matcher] = {
    import scala.collection.JavaConverters._
    val configKey = ConfigUtil.joinPath(name, key)

    if(filtersConfig.hasPath(configKey))
      filtersConfig.getStringList(configKey).asScala.toSeq.map(readMatcher)
    else
      Seq.empty
  }

  private def readMatcher(pattern: String): Matcher = {
    if(pattern.startsWith("regex:"))
      new RegexMatcher(pattern.drop(6))
    else if(pattern.startsWith("glob:"))
      new GlobPathFilter(pattern.drop(5))
    else
      new GlobPathFilter(pattern)
  }
}

class Filters(filters: Map[String, Matcher]) {
  def accept(filterName: String, pattern: String): Boolean =
    filters
      .get(filterName)
      .map(_.accept(pattern))
      .getOrElse(false)

  def get(filterName: String): Matcher = {
    filters.getOrElse(filterName, new Matcher {
      override def accept(name: String): Boolean = false
    })
  }
}

trait Matcher {
  def accept(name: String): Boolean
}

class IncludeExcludeMatcher(includes: Seq[Matcher], excludes: Seq[Matcher]) extends Matcher {
  override def accept(name: String): Boolean =
    includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
}

class RegexMatcher(pattern: String) extends Matcher {
  private val pathRegex = pattern.r

  override def accept(name: String): Boolean = name match {
    case pathRegex(_*) ⇒ true
    case _             ⇒ false
  }
}

class GlobPathFilter(glob: String) extends Matcher {
  private val globPattern = Pattern.compile("(\\*\\*?)|(\\?)|(\\\\.)|(/+)|([^*?]+)")
  private val compiledPattern = getGlobPattern(glob)

  override def accept(name: String): Boolean =
    compiledPattern.matcher(name).matches()

  private def getGlobPattern(glob: String) = {
    val patternBuilder = new StringBuilder
    val matcher = globPattern.matcher(glob)
    while (matcher.find()) {
      val (grp1, grp2, grp3, grp4) = (matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4))
      if (grp1 != null) {
        // match a * or **
        if (grp1.length == 2) {
          // it's a *workers are able to process multiple metrics*
          patternBuilder.append(".*")
        }
        else {
          // it's a *
          patternBuilder.append("[^/]*")
        }
      }
      else if (grp2 != null) {
        // match a '?' glob pattern; any non-slash character
        patternBuilder.append("[^/]")
      }
      else if (grp3 != null) {
        // backslash-escaped value
        patternBuilder.append(Pattern.quote(grp3.substring(1)))
      }
      else if (grp4 != null) {
        // match any number of / chars
        patternBuilder.append("/+")
      }
      else {
        // some other string
        patternBuilder.append(Pattern.quote(matcher.group))
      }
    }

    Pattern.compile(patternBuilder.toString)
  }
}

