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

package kamon.instrumentation.spring.client

import kamon.Kamon
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.instrumentation.http.{HttpClientInstrumentation, HttpMessage}
import org.springframework.web.reactive.function.client.{ClientRequest, ClientResponse}
import reactor.core.publisher.Mono

import java.util.function.Consumer
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable

object ClientInstrumentation {
  private val instrumentation = HttpClientInstrumentation.from(
    Kamon.config().getConfig("kamon.instrumentation.spring.client"),
    "spring.client"
  )

  def getHandler(request: ClientRequest): RequestHandler[ClientRequest] = {
    instrumentation.createHandler(toRequestBuilder(request), Kamon.currentContext())
  }

  private def toRequestBuilder(request: ClientRequest) = {
    new HttpMessage.RequestBuilder[ClientRequest] {
      private val _headers = mutable.Map.empty[String, String]

      /**
        * Returns a new HTTP message containing all headers that have been written to the builder.
        */
      override def build(): ClientRequest = {
        val reqBuilder = ClientRequest.from(request)

        _headers.foreach(header => reqBuilder.header(header._1, header._2))

        reqBuilder.build()
      }

      /**
        * Writes a HTTP header into a HTTP message.
        */
      override def write(header: String, value: String): Unit = _headers += (header -> value)

      /**
        * Request URL.
        */
      override def url: String = request.url().toString

      /**
        * Full request path. Does not include the query.
        */
      override def path: String = request.url().getPath

      /**
        * HTTP Method.
        */
      override def method: String = request.method().toString

      /**
        * Host that will be receiving the request.
        */
      override def host: String = request.url().getHost

      /**
        * Port number at which the request was addressed.
        */
      override def port: Int = request.url().getPort

      /**
        * Reads a single HTTP header value.
        */
      override def read(header: String): Option[String] = Option(request.headers().getFirst(header))

      /**
        * Returns a map with all HTTP headers present in the wrapped HTTP message.
        */
      override def readAll(): Map[String, String] = request
        .headers()
        .toSingleValueMap
        .asScala
        .toMap

    }
  }

  def wrapResponse(mono: Mono[ClientResponse], handler: RequestHandler[ClientRequest]): Mono[ClientResponse] = {
    // This is unnecessarily complicated because scala 2.11 support
    mono.doOnSuccess {
      new Consumer[ClientResponse] {
        override def accept(t: ClientResponse): Unit = {
          handler.processResponse(new HttpMessage.Response {
            override def statusCode: Int = t.statusCode().value()
          })
        }
      }
    }.doOnError(new Consumer[Throwable] {
      override def accept(t: Throwable): Unit = {
        handler.span.fail(t)
        handler.span.finish()
      }
    }).doOnCancel(new Runnable {
      override def run(): Unit = {
        handler.span.fail("Cancelled")
        handler.span.finish()
      }
    })
  }
}
