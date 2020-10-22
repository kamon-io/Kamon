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

trait BaseArmeriaHttpOperationNameGenerator extends HttpOperationNameGenerator {

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def name(request: Request): Option[String] =
    Some(
      localCache.getOrElseUpdate(key(request), {
        // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
        val normalisedPath = normalisePath(request.path)
        name(request, normalisedPath)
      })
    )

  protected def name(request: Request, normalisedPath: String): String

  protected def key(request: Request): String

  private def normalisePath(path: String): String = {
    val p = normalizePattern.replaceAllIn(path, "$1").replace('/', '.').dropWhile(_ == '.')
    val normalisedPath = {
      if (p.lastOption.exists(_ != '.')) s"$p."
      else p
    }
    normalisedPath
  }
}
