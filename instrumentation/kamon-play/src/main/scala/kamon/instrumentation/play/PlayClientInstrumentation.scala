/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.play

import java.util.concurrent.Callable

import kamon.Kamon
import kamon.instrumentation.http.{HttpClientInstrumentation, HttpMessage}
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}
import play.api.libs.ws.{StandaloneWSRequest, StandaloneWSResponse, WSRequestExecutor, WSRequestFilter}

import scala.concurrent.Future

class PlayClientInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("play.api.libs.ws.StandaloneWSClient")
    .intercept(method("url"), classOf[WSClientUrlInterceptor])
}

class WSClientUrlInterceptor
object WSClientUrlInterceptor {

  @RuntimeType
  def url(@SuperCall zuper: Callable[StandaloneWSRequest]): StandaloneWSRequest = {
    zuper
      .call()
      .withRequestFilter(_clientInstrumentationFilter)
  }

  @volatile private var _httpClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation
  Kamon.onReconfigure(_ => _httpClientInstrumentation = rebuildHttpClientInstrumentation())

  private def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.play.http.client")
    _httpClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "play.http.client")
    _httpClientInstrumentation
  }

  private val _clientInstrumentationFilter = WSRequestFilter { rf: WSRequestExecutor =>
    new WSRequestExecutor {
      override def apply(request: StandaloneWSRequest): Future[StandaloneWSResponse] = {
        val currentContext = Kamon.currentContext()
        val requestHandler = _httpClientInstrumentation.createHandler(toRequestBuilder(request), currentContext)
        val responseFuture = Kamon.runWithSpan(requestHandler.span, finishSpan = false) {
          rf(requestHandler.request)
        }

        responseFuture.transform(
          s = response => {
            requestHandler.processResponse(toResponse(response))
            response
          },
          f = error => {
            requestHandler.span.fail(error).finish()
            error
          }
        )(CallingThreadExecutionContext)
      }
    }
  }

  private def toRequestBuilder(request: StandaloneWSRequest): HttpMessage.RequestBuilder[StandaloneWSRequest] =
    new HttpMessage.RequestBuilder[StandaloneWSRequest] {
      private var _newHttpHeaders: List[(String, String)] = List.empty

      override def write(header: String, value: String): Unit =
        _newHttpHeaders = (header -> value) :: _newHttpHeaders

      override def build(): StandaloneWSRequest =
        request.addHttpHeaders(_newHttpHeaders: _*)

      override def read(header: String): Option[String] =
        request.header(header)

      override def readAll(): Map[String, String] =
        request.headers.mapValues(_.head).toMap

      override def url: String =
        request.url

      override def path: String =
        request.uri.getPath

      override def method: String =
        request.method

      override def host: String =
        request.uri.getHost

      override def port: Int =
        request.uri.getPort
    }

  private def toResponse(response: StandaloneWSResponse): HttpMessage.Response = new HttpMessage.Response {
    override def statusCode: Int = response.status
  }
}
