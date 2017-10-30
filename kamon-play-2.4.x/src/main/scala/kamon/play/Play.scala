/*
 * =========================================================================================
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
package play

import _root_.play.api.libs.ws.WSRequest
import _root_.play.api.mvc.RequestHeader
import com.typesafe.config.Config
import kamon.util.DynamicAccess

object Play {
  @volatile private var nameGenerator: NameGenerator = new DefaultNameGenerator()
  loadConfiguration(Kamon.config())

  def generateOperationName(requestHeader: RequestHeader): String =
    nameGenerator.generateOperationName(requestHeader)

  def generateHttpClientOperationName(request: WSRequest): String =
    nameGenerator.generateHttpClientOperationName(request)

  private def loadConfiguration(config: Config): Unit = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString("kamon.play.name-generator")
    nameGenerator =  dynamic.createInstanceFor[NameGenerator](nameGeneratorFQCN, Nil).get
  }

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      Play.loadConfiguration(newConfig)
  })
}

trait NameGenerator {
  def generateOperationName(requestHeader: RequestHeader): String
  def generateHttpClientOperationName(request: WSRequest): String
}

class DefaultNameGenerator extends NameGenerator {
  import _root_.scala.collection.concurrent.TrieMap
  import _root_.play.api.routing.Router
  import java.util.Locale

  private val cache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def generateOperationName(requestHeader: RequestHeader): String = requestHeader.tags.get(Router.Tags.RouteVerb).map { verb ⇒
    val path = requestHeader.tags(Router.Tags.RoutePattern)
    cache.atomicGetOrElseUpdate(s"$verb$path", {
      val operationName = {
        // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
        val p = normalizePattern.replaceAllIn(path, "$1").replace('/', '.').dropWhile(_ == '.')
        val normalisedPath = {
          if (p.lastOption.exists(_ != '.')) s"$p."
          else p
        }
        s"$normalisedPath${verb.toLowerCase(Locale.ENGLISH)}"
      }
      operationName
    })
  } getOrElse "UntaggedOperation"

  def generateHttpClientOperationName(request: WSRequest): String = request.uri.getAuthority
}
