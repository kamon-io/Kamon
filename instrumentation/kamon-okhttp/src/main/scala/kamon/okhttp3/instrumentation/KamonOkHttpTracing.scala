/*
 * =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
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

package kamon.okhttp3.instrumentation

import java.util

import kamon.Kamon
import kamon.context.HttpPropagation.HeaderWriter
import kamon.instrumentation.http.{HttpClientInstrumentation, HttpMessage}
import okhttp3.{Request, Response}

import scala.collection.immutable.Map
import scala.collection.{JavaConverters, mutable}

object KamonOkHttpTracing {
  private val httpClientConfig = Kamon.config.getConfig("kamon.instrumentation.okhttp.http-client")
  private val instrumentation = HttpClientInstrumentation.from(httpClientConfig, "okhttp-client")

  def withNewSpan(request: Request): HttpClientInstrumentation.RequestHandler[Request] = {
    instrumentation.createHandler(getRequestBuilder(request), Kamon.currentContext)
  }

  def successContinuation(requestHandler: HttpClientInstrumentation.RequestHandler[Request], response: Response): Response = {
    requestHandler.processResponse(toKamonResponse(response))
    response
  }

  def failureContinuation(requestHandler: HttpClientInstrumentation.RequestHandler[Request], error: Throwable): Unit = {
    requestHandler.span.fail(error)
    requestHandler.span.finish()
  }

  def getRequestBuilder(request: Request): HttpMessage.RequestBuilder[Request] = new HttpMessage.RequestBuilder[Request]() {
    private val _headers = mutable.Map[String, String]()

    override def read(header: String): Option[String] = Option.apply(request.header(header))

    override def readAll: Map[String, String] = {
      JavaConverters
        .mapAsScalaMapConverter(request.headers.toMultimap)
        .asScala
        .mapValues((values: util.List[String]) => values.get(0))
        .toMap
    }

    override def url: String = request.url.toString

    override def path: String = request.url.uri.getPath

    override def method: String = request.method

    override def host: String = request.url.host

    override def port: Int = request.url.port

    override def write(header: String, value: String): Unit = {
      _headers += (header -> value)
    }

    override def build: Request = {
      val newHeadersMap = request.headers.newBuilder
      _headers.foreach { case (key, value) => newHeadersMap.add(key, value) }
      request.newBuilder.headers(newHeadersMap.build).build
    }
  }

  def toKamonResponse(response: Response): HttpMessage.Response = new HttpMessage.Response() {
    override def statusCode: Int = response.code()
  }

  trait HeaderHandler extends HeaderWriter {
    private val _headers = mutable.Map[String, String]()

    override def write(header: String, value: String): Unit = {
      _headers += (header -> value)
    }

    def headers: mutable.Map[String, String] = _headers
  }

}
