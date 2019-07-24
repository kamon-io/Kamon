/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.akka.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import kamon.Kamon
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.akka.http.AkkaHttpInstrumentation.{toRequestBuilder, toResponse}
import kamon.trace.Span
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.Bridge
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{Argument, RuntimeType, This}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class AkkaHttpClientInstrumentation extends InstrumentationBuilder {

  /**
    * Simply modifies the requests as they are submitted. This does not cover connection pooling, just requests sent
    * via the Http.singleRequest mechanism.
    */
  onType("akka.http.scaladsl.HttpExt")
    .bridge(classOf[SingleRequestImplBridge])
    .intercept(method("singleRequest"), classOf[HttpExtSingleRequestAdvice])
}

trait SingleRequestImplBridge {

  @Bridge("scala.concurrent.Future singleRequestImpl(akka.http.scaladsl.model.HttpRequest, akka.http.scaladsl.HttpsConnectionContext, akka.http.scaladsl.settings.ConnectionPoolSettings, akka.event.LoggingAdapter)")
  def invokeSingleRequestImpl(request: HttpRequest, connectionContext: HttpsConnectionContext,
    settings: ConnectionPoolSettings, log: LoggingAdapter): Future[HttpResponse]
}

class HttpExtSingleRequestAdvice
object HttpExtSingleRequestAdvice {

  @volatile private var _httpClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation

  private[http] def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.akka.http.client")
    _httpClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "akka.http.client")
    _httpClientInstrumentation
  }

  @RuntimeType
  def singleRequest(@This httpExt: Any, @Argument(0) request: HttpRequest, @Argument(1) connectionContext: HttpsConnectionContext,
      @Argument(2) settings: ConnectionPoolSettings, @Argument(3) log: LoggingAdapter): Future[HttpResponse] = {

    val requestBuilder = toRequestBuilder(request)
    val handler = _httpClientInstrumentation.createHandler(requestBuilder, Kamon.currentContext())

    val responseFuture = Kamon.runWithSpan(handler.span, finishSpan = false) {
      httpExt.asInstanceOf[SingleRequestImplBridge].invokeSingleRequestImpl(
        handler.request, connectionContext, settings, log
      )
    }

    responseFuture.onComplete {
      case Success(response) => handler.processResponse(toResponse(response))
      case Failure(t) => handler.span.fail(t).finish()
    }(CallingThreadExecutionContext)

    responseFuture
  }
}