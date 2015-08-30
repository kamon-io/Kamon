/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.play

import akka.actor._
import kamon.Kamon
import kamon.util.http.HttpServerMetrics
import org.slf4j.LoggerFactory
import play.api.libs.ws.WSRequest
import play.api.mvc.RequestHeader

object PlayExtension {
  val SegmentLibraryName = "WS-client"

  val log = LoggerFactory.getLogger("kamon.play.PlayExtension")
  private val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)

  private val config = Kamon.config.getConfig("kamon.play")
  val httpServerMetrics = Kamon.metrics.entity(HttpServerMetrics, "play-server")

  val includeTraceToken: Boolean = config.getBoolean("automatic-trace-token-propagation")
  val traceTokenHeaderName: String = config.getString("trace-token-header-name")

  private val nameGeneratorFQN = config.getString("name-generator")
  private val nameGenerator: NameGenerator = dynamic.createInstanceFor[NameGenerator](nameGeneratorFQN, Nil).get

  def generateTraceName(requestHeader: RequestHeader): String = nameGenerator.generateTraceName(requestHeader)
  def generateHttpClientSegmentName(request: WSRequest): String = nameGenerator.generateHttpClientSegmentName(request)
}

trait NameGenerator {
  def generateTraceName(requestHeader: RequestHeader): String
  def generateHttpClientSegmentName(request: WSRequest): String
}

class DefaultNameGenerator extends NameGenerator {
  import scala.collection.concurrent.TrieMap
  import play.api.Routes
  import java.util.Locale
  import kamon.util.TriemapAtomicGetOrElseUpdate.Syntax

  private val cache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  def generateTraceName(requestHeader: RequestHeader): String = requestHeader.tags.get(Routes.ROUTE_VERB).map { verb ⇒
    val path = requestHeader.tags(Routes.ROUTE_PATTERN)
    cache.atomicGetOrElseUpdate(s"$verb$path", {
      val traceName = {
        // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
        val p = normalizePattern.replaceAllIn(path, "$1").replace('/', '.').dropWhile(_ == '.')
        val normalisedPath = {
          if (p.lastOption.exists(_ != '.')) s"$p."
          else p
        }
        s"$normalisedPath${verb.toLowerCase(Locale.ENGLISH)}"
      }
      traceName
    })
  } getOrElse "UntaggedTrace"

  def generateHttpClientSegmentName(request: WSRequest): String = request.url
}
