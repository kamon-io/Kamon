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

import com.typesafe.config.Config

import scala.collection.JavaConverters.collectionAsScalaIterableConverter


/**
  * Decides whether a given String satisfy a predicate. Composite Filters with include/exclude patters are used by
  * Kamon as a generic configuration abstraction that provides simple yet flexible filtering capabilities.
  */
trait Filter {

  /** Returns true if the provided value satisfy the filter's predicate */
  def accept(value: String): Boolean

}

object Filter {

  /** Filter that accepts any value */
  val Accept = Filter.Constant(true)

  /** Filter that does not accept any value */
  val Deny = Filter.Constant(false)

  /**
    * Creates a new composite Filter by looking up the provided path on Kamon's configuration. All inputs matching any
    * of the include filters and none of the exclude filters will be accepted. The configuration is expected to have the
    * following structure:
    *
    * config {
    *   includes = [ "some/pattern", "regex:some[0-9]" ]
    *   excludes = [ ]
    * }
    *
    * By default, the patterns are treated as Glob patterns but users can explicitly configure the pattern type by
    * prefixing the pattern with either "glob:" or "regex:". If any of the elements are missing they will be considered
    * empty.
    */
  def from(path: String): Filter =
    if(Kamon.config().hasPath(path))
      from(Kamon.config().getConfig(path))
    else
      Filter.Deny

  /**
    * Creates a new composite Filter with matchers from the provided configuration. All inputs matching any of the
    * include filters and none of the exclude filters will be accepted. The configuration is expected to have the
    * following structure:
    *
    * config {
    *   includes = [ "some/pattern", "regex:some[0-9]" ]
    *   excludes = [ ]
    * }
    *
    * By default the patterns are treated as Glob patterns but users can explicitly configure the pattern type by
    * prefixing the pattern with either "glob:" or "regex:". If any of the elements are missing they will be considered
    * empty.
    */
  def from(config: Config): Filter = {
    val includes = readFilters(config, "includes")
    val excludes = readFilters(config, "excludes")

    if(includes.isEmpty)
      Filter.Deny
    else
      new Filter.IncludeExclude(includes, excludes)
  }

  private def readFilters(filtersConfig: Config, key: String): Seq[Filter] =
    if(filtersConfig.hasPath(key))
      filtersConfig.getStringList(key).asScala.map(readFilter).toSeq
    else
      Seq.empty

  private def readFilter(pattern: String): Filter = {
    if(pattern.startsWith("regex:"))
      new Filter.Regex(pattern.drop(6))
    else if(pattern.startsWith("glob:"))
      new Filter.Glob(pattern.drop(5))
    else
      new Filter.Glob(pattern)
  }

  /**
    * Composite Filter that accepts inputs that match at least one of the include filters and none of the exclude
    * filters.
    */
  case class IncludeExclude(includes: Seq[Filter], excludes: Seq[Filter]) extends Filter {
    override def accept(name: String): Boolean =
      includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
  }

  /** Filter that always returns the same result, regardless of the pattern */
  case class Constant(result: Boolean) extends Filter {
    override def accept(value: String): Boolean = result
  }

  /** Filter that uses regexes to match values */
  case class Regex(pattern: String) extends Filter {
    private val pathRegex = pattern.r

    override def accept(name: String): Boolean = name match {
      case pathRegex(_*) ⇒ true
      case _             ⇒ false
    }
  }

  /** Filter that uses a glob pattern expression to match values. */
  case class Glob(glob: String) extends Filter {
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
}