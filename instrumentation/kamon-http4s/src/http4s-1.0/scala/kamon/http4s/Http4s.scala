/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.http4s

import com.typesafe.config.Config
import kamon.util.DynamicAccess
import kamon.Kamon
import kamon.instrumentation.http.{HttpMessage, HttpOperationNameGenerator}

object Http4s {
  @volatile var nameGenerator: HttpOperationNameGenerator =
    nameGeneratorFromConfig(Kamon.config())

  private def nameGeneratorFromConfig(
      config: Config
  ): HttpOperationNameGenerator = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString(
      "kamon.instrumentation.http4s.client.tracing.operations.name-generator"
    )
    dynamic
      .createInstanceFor[HttpOperationNameGenerator](nameGeneratorFQCN, Nil)
  }

  Kamon.onReconfigure { newConfig =>
    nameGenerator = nameGeneratorFromConfig(newConfig)
  }
}

class DefaultNameGenerator extends HttpOperationNameGenerator {

  import java.util.Locale

  import scala.collection.concurrent.TrieMap

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  override def name(request: HttpMessage.Request): Option[String] = {
    Some(
      localCache.getOrElseUpdate(
        s"${request.method}${request.path}", {
          // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
          val p = normalizePattern
            .replaceAllIn(request.path, "$1")
            .replace('/', '.')
            .dropWhile(_ == '.')
          val normalisedPath = {
            if (p.lastOption.exists(_ != '.')) s"$p."
            else p
          }
          s"$normalisedPath${request.method.toLowerCase(Locale.ENGLISH)}"
        }
      )
    )
  }
}
