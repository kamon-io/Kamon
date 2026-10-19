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

package kamon.instrumentation.akka.http

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import kamon.Kamon
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.akka.http.AkkaHttpInstrumentation.toResponse
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.util.CallingThreadExecutionContext
import kanela.agent.api.instrumentation.InstrumentationBuilder

import scala.concurrent.Future
import scala.util.{Failure, Success}

class AkkaHttpClientInstrumentation extends InstrumentationBuilder with VersionFiltering {

  /**
    * Simply modifies the requests as they are submitted. This does not cover connection pooling, just requests sent
    * via the Http.singleRequest mechanism.
    */

  onAkkaHttp("10.1") {
    onType("akka.http.scaladsl.HttpExt")
      .advise(method("singleRequestImpl"), classOf[HttpExtSingleRequestAdvice])
  }

  onType("akka.http.impl.engine.client.PoolMaster")
    .advise(method("dispatchRequest"), classOf[PoolMasterDispatchRequestAdvice])
}

object AkkaHttpClientInstrumentation {

  @volatile var httpClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation

  private[http] def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.akka.http.client")
    httpClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "akka.http.client")
    httpClientInstrumentation
  }

  def handleResponse(
    responseFuture: Future[HttpResponse],
    handler: RequestHandler[HttpRequest]
  ): Future[HttpResponse] = {
    responseFuture.onComplete {
      case Success(response) => handler.processResponse(toResponse(response))
      case Failure(t)        => handler.span.fail(t).finish()
    }(CallingThreadExecutionContext)

    responseFuture
  }
}
