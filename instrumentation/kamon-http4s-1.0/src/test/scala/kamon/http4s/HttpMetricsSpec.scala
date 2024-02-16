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

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits._
import kamon.http4s.middleware.server.KamonSupport
import kamon.instrumentation.http.HttpServerMetrics
import kamon.testkit.{InstrumentInspection, InitAndStopKamonAfterAll}
import org.http4s.HttpRoutes
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Server
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class HttpMetricsSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with InstrumentInspection.Syntax
    with OptionValues
    with InitAndStopKamonAfterAll {

  val srv =
    BlazeServerBuilder[IO](global.compute)
      .bindLocal(43567)
      .withHttpApp(
        KamonSupport(
          HttpRoutes.of[IO] {
            case GET -> Root / "tracing" / "ok"        => Ok("ok")
            case GET -> Root / "tracing" / "not-found" => NotFound("not-found")
            case GET -> Root / "tracing" / "error" =>
              InternalServerError("This page will generate an error!")
          },
          "/127.0.0.1",
          43567
        ).orNotFound
      )
      .resource

  val client =
    BlazeClientBuilder[IO](global.compute).withMaxTotalConnections(10).resource

  val metrics =
    Resource.eval(
      IO(HttpServerMetrics.of("http4s.server", "/127.0.0.1", 43567))
    )

  def withServerAndClient[A](
      f: (Server, Client[IO], HttpServerMetrics.HttpServerInstruments) => IO[A]
  ): A =
    (srv, client, metrics).tupled.use(f.tupled).unsafeRunSync()

  private def get[F[_]: Concurrent](
      path: String
  )(server: Server, client: Client[F]): F[String] = {
    client.expect[String](s"http://127.0.0.1:${server.address.port}$path")
  }

  "The HttpMetrics" should {

    "track the total of active requests" in withServerAndClient {
      (server, client, serverMetrics) =>
        val requests = List
          .fill(100) {
            get("/tracing/ok")(server, client)
          }
          .parSequence_

        val test = IO {
          serverMetrics.activeRequests.distribution().max should be > 1L
          serverMetrics.activeRequests.distribution().min shouldBe 0L
        }
        requests *> IO.sleep(2.seconds) *> test
    }

    "track the response time with status code 2xx" in withServerAndClient {
      (server, client, serverMetrics) =>
        val requests: IO[Unit] =
          List.fill(100)(get("/tracing/ok")(server, client)).sequence_

        val test = IO(serverMetrics.requestsSuccessful.value() should be >= 0L)

        requests *> test
    }

    "track the response time with status code 4xx" in withServerAndClient {
      (server, client, serverMetrics) =>
        val requests: IO[Unit] = List
          .fill(100)(get("/tracing/not-found")(server, client).attempt)
          .sequence_

        val test = IO(serverMetrics.requestsClientError.value() should be >= 0L)

        requests *> test
    }

    "track the response time with status code 5xx" in withServerAndClient {
      (server, client, serverMetrics) =>
        val requests: IO[Unit] = List
          .fill(100)(get("/tracing/error")(server, client).attempt)
          .sequence_

        val test = IO(serverMetrics.requestsServerError.value() should be >= 0L)

        requests *> test
    }
  }
}
