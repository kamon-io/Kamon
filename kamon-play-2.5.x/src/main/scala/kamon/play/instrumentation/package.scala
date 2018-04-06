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

package kamon.play

import io.netty.handler.codec.http.HttpRequest
import kamon.Kamon
import kamon.context.{Context, TextMap}
import play.api.libs.ws.WSRequest

package object instrumentation {

  def encodeContext(ctx:Context, request:WSRequest): WSRequest = {
    val textMap = Kamon.contextCodec().HttpHeaders.encode(ctx)
    request.withHeaders(textMap.values.toSeq: _*)
  }

  def decodeContext(request: HttpRequest): Context = {
    val headersTextMap = readOnlyTextMapFromHeaders(request)
    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  }

  private def readOnlyTextMapFromHeaders(request: HttpRequest): TextMap = new TextMap {
    override def values: Iterator[(String, String)] = Iterator.empty
    override def get(key: String): Option[String] = Option(request.headers().get(key))
    override def put(key: String, value: String): Unit = {}
  }

  def isError(statusCode: Int): Boolean =
    statusCode >= 500 && statusCode < 600

  object StatusCodes {
    val NotFound = 404
  }
}
