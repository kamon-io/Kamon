/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package util

import java.util.regex.Pattern

import com.typesafe.config.Config

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
  * Decides whether a given String satisfy a group of includes and excludes patterns.
  */
trait Filter {

  /**
    * Returns true if the provided test string satisfies at least one of the includes patterns and none of the excludes
    * patterns in the filter.
    */
  def accept(value: String): Boolean

  /**
    * Returns whether the provided test string satisfies any of the filter's includes patterns.
    */
  def includes(value: String): Boolean

  /**
    * Returns whether the provided test string satisfies any of the filter's excludes patterns.
    */
  def excludes(value: String): Boolean

}

object Filter {

  /**
    * Filter that accepts any value
    */
  val Accept = Filter.Constant(true)

  /**
    * Filter that does not accept any value
    */
  val Deny = Filter.Constant(false)

  /**
    * Creates a new Filter from the provided path on Kamon's configuration. The configuration is expected to have the
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
    if (Kamon.config().hasPath(path))
      from(Kamon.config().getConfig(path))
    else
      Filter.Deny

  /**
    * Creates a new Filter from the provided config. The configuration is expected to have the following structure:
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

    if (includes.isEmpty)
      Filter.Deny
    else
      new Filter.IncludeExclude(includes, excludes)
  }

  /**
    * Creates a new Filter from a single glob pattern.
    */
  def fromGlob(glob: String): Filter =
    Filter.SingleMatcher(Glob(glob))

  /**
    * Creates a new Filter from a single regex pattern.
    */
  def fromRegex(regexPattern: String): Filter =
    Filter.SingleMatcher(Regex(regexPattern))

  private def readFilters(filtersConfig: Config, key: String): Seq[Filter.Matcher] =
    if (filtersConfig.hasPath(key))
      filtersConfig.getStringList(key).asScala.map(readMatcher).toSeq
    else
      Seq.empty

  private def readMatcher(pattern: String): Filter.Matcher = {
    if (pattern.startsWith("regex:"))
      new Filter.Regex(pattern.drop(6))
    else if (pattern.startsWith("glob:"))
      new Filter.Glob(pattern.drop(5))
    else
      new Filter.Glob(pattern)
  }

  /**
    * Composite Filter that accepts inputs that match at least one of the include filters and none of the exclude
    * filters.
    */
  case class IncludeExclude(includeFilters: Seq[Filter.Matcher], excludeFilters: Seq[Filter.Matcher]) extends Filter {

    override def accept(test: String): Boolean =
      includes(test) && !excludes(test)

    override def includes(test: String): Boolean =
      includeFilters.exists(_.accept(test))

    override def excludes(test: String): Boolean =
      excludeFilters.exists(_.accept(test))
  }

  /**
    * Filter that uses a single pattern to accept test strings.
    */
  case class SingleMatcher(matcher: Matcher) extends Filter {

    override def accept(value: String): Boolean =
      matcher.accept(value)

    override def includes(value: String): Boolean =
      matcher.accept(value)

    override def excludes(value: String): Boolean =
      !matcher.accept(value)
  }

  /**
    * Base trait for all matcher implementations
    */
  trait Matcher {
    def accept(value: String): Boolean
  }

  /**
    * Filter that always returns the same result, regardless of the pattern
    */
  case class Constant(result: Boolean) extends Filter {
    override def accept(value: String): Boolean = result
    override def includes(value: String): Boolean = result
    override def excludes(value: String): Boolean = false
  }

  /**
    * Matcher that uses regexes to match values.
    */
  case class Regex(pattern: String) extends Matcher {
    private val _pathRegex = pattern.r

    override def accept(value: String): Boolean = value match {
      case _pathRegex(_*) => true
      case _              => false
    }
  }

  /**
    * Matcher that uses a glob pattern expression to match values.
    */
  case class Glob(glob: String) extends Matcher {
    private val _globPattern = Pattern.compile("(\\*\\*?)|(\\?)|(\\\\.)|(/+)|([^*?]+)")
    private val _compiledPattern = getGlobPattern(glob)

    override def accept(name: String): Boolean =
      _compiledPattern.matcher(name).matches()

    private def getGlobPattern(glob: String) = {
      val patternBuilder = new StringBuilder
      val matcher = _globPattern.matcher(glob)
      while (matcher.find()) {
        val (grp1, grp2, grp3, grp4) = (matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4))
        if (grp1 != null) {
          // match a * or **
          if (grp1.length == 2) {
            // it's a *workers are able to process multiple metrics*
            patternBuilder.append(".*")
          } else {
            // it's a *
            patternBuilder.append("[^/]*")
          }
        } else if (grp2 != null) {
          // match a '?' glob pattern; any non-slash character
          patternBuilder.append("[^/]")
        } else if (grp3 != null) {
          // backslash-escaped value
          patternBuilder.append(Pattern.quote(grp3.substring(1)))
        } else if (grp4 != null) {
          // match any number of / chars
          patternBuilder.append("/+")
        } else {
          // some other string
          patternBuilder.append(Pattern.quote(matcher.group))
        }
      }

      Pattern.compile(patternBuilder.toString)
    }
  }
}
