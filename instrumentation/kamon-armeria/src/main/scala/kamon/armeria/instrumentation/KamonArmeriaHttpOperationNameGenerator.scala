/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.armeria.instrumentation

import kamon.instrumentation.http.HttpMessage.Request
import kamon.instrumentation.http.HttpOperationNameGenerator

import scala.collection.concurrent.TrieMap

class KamonArmeriaHttpOperationNameGenerator extends HttpOperationNameGenerator {

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def name(request: Request): Option[String] =
    Some(
      localCache.getOrElseUpdate(request.path, {
        normalisePath(request.path)
      })
    )

  private def normalisePath(path: String): String = {
    val p = normalizePattern.replaceAllIn(path, "$1").dropWhile(_ == '.')
    val normalisedPath = {
      if (p.lastOption.contains('/')) p.dropRight(1)
      else p
    }
    normalisedPath.split("\\.").last
  }
}

object KamonArmeriaHttpOperationNameGenerator {
  def apply(): KamonArmeriaHttpOperationNameGenerator = new KamonArmeriaHttpOperationNameGenerator()
}
