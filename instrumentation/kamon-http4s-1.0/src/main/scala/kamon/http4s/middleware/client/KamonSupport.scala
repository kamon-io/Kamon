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
package middleware.client

import cats.effect.{Sync, Resource}
import cats.implicits._
import com.typesafe.config.Config
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.http.HttpClientInstrumentation
import org.http4s.{Request, Response}
import org.http4s.client.Client

object KamonSupport {

  private var _instrumentation = instrumentation(Kamon.config())

  private def instrumentation(
      kamonConfig: Config
  ): HttpClientInstrumentation = {
    val httpClientConfig =
      kamonConfig.getConfig("kamon.instrumentation.http4s.client")
    HttpClientInstrumentation.from(httpClientConfig, "http4s.client")
  }

  Kamon.onReconfigure(newConfig =>
    _instrumentation = instrumentation(newConfig)
  )

  def apply[F[_]](underlying: Client[F])(implicit F: Sync[F]): Client[F] =
    Client { request =>
      // this needs to run on the same thread as the caller, so can't be suspended in F
      val ctx = Kamon.currentContext()
      kamonClient(underlying)(request)(ctx)(_instrumentation)
    }

  private def kamonClient[F[_]](
      underlying: Client[F]
  )(request: Request[F])(ctx: Context)(
      instrumentation: HttpClientInstrumentation
  )(implicit F: Sync[F]): Resource[F, Response[F]] =
    for {
      requestHandler <- Resource.eval(
        F.delay(instrumentation.createHandler(getRequestBuilder(request), ctx))
      )
      response <- underlying.run(requestHandler.request).attempt
      trackedResponse <- Resource.eval(handleResponse(response, requestHandler))
    } yield trackedResponse

  def handleResponse[F[_]](
      response: Either[Throwable, Response[F]],
      requestHandler: HttpClientInstrumentation.RequestHandler[Request[F]]
  )(implicit F: Sync[F]): F[Response[F]] =
    response match {
      case Right(res) =>
        requestHandler.processResponse(getResponseBuilder(res))
        F.delay(res)
      case Left(error) =>
        requestHandler.span.fail(error).finish()
        F.raiseError(error)
    }

}
