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

package kamon.util

import scala.util.matching.Regex

class GlobPathFilter(glob: String) {

  val regex = globAsRegex(glob)

  def accept(path: String): Boolean = path.matches(regex.toString())

  private def globAsRegex(glob: String): Regex = {
    val regexStr = new StringBuilder('^')

    glob.foreach {
      case '.' ⇒ regexStr append """\."""
      case '*' ⇒ regexStr append """[^/]*"""
      case '?' ⇒ regexStr append """."""
      case ch  ⇒ regexStr append ch
    }
    regexStr append """/?$"""

    new Regex(regexStr.toString())
  }
}

object GlobPathFilter {
  def apply(glob: String): GlobPathFilter = new GlobPathFilter(glob)
}
