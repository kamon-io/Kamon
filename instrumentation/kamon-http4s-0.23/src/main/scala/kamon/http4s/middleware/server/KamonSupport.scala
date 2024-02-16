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
package middleware.server

import cats.data.{Kleisli, OptionT}
import cats.effect.{Resource, Sync}
import cats.implicits._
import kamon.Kamon
import kamon.context.Storage
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.instrumentation.http.HttpServerInstrumentation
import org.http4s.{HttpRoutes, Request, Response}

object KamonSupport {

  def apply[F[_]: Sync](
      service: HttpRoutes[F],
      interface: String,
      port: Int
  ): HttpRoutes[F] = {
    val httpServerConfig =
      Kamon.config().getConfig("kamon.instrumentation.http4s.server")
    val instrumentation = HttpServerInstrumentation.from(
      httpServerConfig,
      "http4s.server",
      interface,
      port
    )

    Kleisli(kamonService[F](service, instrumentation)(_))
  }

  private def kamonService[F[_]](
      service: HttpRoutes[F],
      instrumentation: HttpServerInstrumentation
  )(request: Request[F])(implicit F: Sync[F]): OptionT[F, Response[F]] =
    OptionT {
      getHandler(instrumentation)(request).use { handler =>
        for {
          resOrUnhandled <- service(request).value.attempt
          respWithContext <- kamonServiceHandler(
            handler,
            resOrUnhandled,
            instrumentation.settings
          )
        } yield respWithContext
      }
    }

  private def processRequest[F[_]](
      requestHandler: RequestHandler
  )(implicit F: Sync[F]): Resource[F, RequestHandler] =
    Resource.make(F.delay(requestHandler.requestReceived()))(h =>
      F.delay(h.responseSent())
    )

  private def withContext[F[_]](
      requestHandler: RequestHandler
  )(implicit F: Sync[F]): Resource[F, Storage.Scope] =
    Resource.make(F.delay(Kamon.storeContext(requestHandler.context)))(scope =>
      F.delay(scope.close())
    )

  private def getHandler[F[_]](
      instrumentation: HttpServerInstrumentation
  )(request: Request[F])(implicit F: Sync[F]): Resource[F, RequestHandler] =
    for {
      handler <- Resource.eval(
        F.delay(instrumentation.createHandler(buildRequestMessage(request)))
      )
      _ <- processRequest(handler)
      _ <- withContext(handler)
    } yield handler

  private def kamonServiceHandler[F[_]](
      requestHandler: RequestHandler,
      e: Either[Throwable, Option[Response[F]]],
      settings: HttpServerInstrumentation.Settings
  )(implicit F: Sync[F]): F[Option[Response[F]]] =
    e match {
      case Left(e) =>
        F.delay {
          requestHandler.span.fail(e.getMessage)
          Some(
            requestHandler.buildResponse(
              errorResponseBuilder,
              requestHandler.context
            )
          )
        } *> F.raiseError(e)
      case Right(None) =>
        F.delay {
          requestHandler.span.name(settings.unhandledOperationName)
          val response: Response[F] = requestHandler.buildResponse[Response[F]](
            notFoundResponseBuilder,
            requestHandler.context
          )
          Some(response)
        }
      case Right(Some(response)) =>
        F.delay {
          val a = requestHandler.buildResponse(
            getResponseBuilder(response),
            requestHandler.context
          )
          Some(a)
        }
    }

}
