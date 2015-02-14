/* =========================================================================================
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

import sbt.Process

object VersionWithSHA {

  private lazy val VersionWithShaRegex = """(?:\d+\.)?(?:\d+\.)?(?:\d+)-[0-9a-f]{5,40}"""

  /** Don't use this. You should use version.value instead. */
  def kamonVersionWithSHA(version: String) = version.takeWhile(_ != '-') + "-" + Process("git rev-parse HEAD").lines.head

  /** Don't use this. You should use isSnapshot.value instead. */
  def kamonIsSnapshot(version: String) = (version matches VersionWithShaRegex) || (version endsWith "-SNAPSHOT")

}