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

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import kamon.Kamon
import kamon.http4s.middleware.client.KamonSupport
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.{TestSpanReporter, InitAndStopKamonAfterAll}
import kamon.trace.Span
import org.http4s.client._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Response}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.OptionValues

import java.net.ConnectException
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ClientInstrumentationSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with OptionValues
    with TestSpanReporter
    with InitAndStopKamonAfterAll {

  val service = HttpRoutes.of[IO] {
    case GET -> Root / "tracing" / "ok"        => Ok("ok")
    case GET -> Root / "tracing" / "not-found" => NotFound("not-found")
    case GET -> Root / "tracing" / "error" =>
      InternalServerError("This page will generate an error!")
  }

  val client: Client[IO] =
    KamonSupport[IO](Client.fromHttpApp[IO](service.orNotFound))

  "The Client instrumentation" should {
    "propagate the current context and generate a span inside an action and complete the ws request" in {
      val okSpan = Kamon.spanBuilder("ok-operation-span").start()

      Kamon.runWithSpan(okSpan) {
        client.expect[String]("/tracing/ok").unsafeRunSync() shouldBe "ok"
      }

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "/tracing/ok"
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "http4s.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(
          plain("parentOperation")
        ) shouldBe "ok-operation-span"

        okSpan.id == span.parentId
      }
    }

    "close and finish a span even if an exception is thrown by the client" in {
      val okSpan = Kamon.spanBuilder("client-exception").start()
      val client: Client[IO] = KamonSupport[IO](
        Client(_ =>
          Resource.eval(
            IO.raiseError[Response[IO]](
              new ConnectException("Connection Refused.")
            )
          )
        )
      )

      Kamon.runWithSpan(okSpan) {
        a[ConnectException] should be thrownBy {
          client.expect[String]("/tracing/ok").unsafeRunSync()
        }
      }

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/tracing/ok"
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "http4s.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.hasError shouldBe true

        okSpan.id == span.parentId
      }
    }

    "propagate the current context and generate a span called not-found and complete the ws request" in {
      val notFoundSpan = Kamon.spanBuilder("not-found-operation-span").start()

      Kamon.runWithSpan(notFoundSpan) {
        client
          .expect[String]("/tracing/not-found")
          .attempt
          .unsafeRunSync()
          .isLeft shouldBe true
      }

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/tracing/not-found"
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "http4s.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 404
        span.metricTags.get(
          plain("parentOperation")
        ) shouldBe "not-found-operation-span"

        notFoundSpan.id == span.parentId
      }
    }

    "propagate the current context and generate a span with error and complete the ws request" in {
      val errorSpan = Kamon.spanBuilder("error-operation-span").start()

      Kamon.runWithSpan(errorSpan) {
        client
          .expect[String]("/tracing/error")
          .attempt
          .unsafeRunSync()
          .isLeft shouldBe true
      }

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "/tracing/error"
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "http4s.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.hasError shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.metricTags.get(
          plain("parentOperation")
        ) shouldBe "error-operation-span"

        errorSpan.id == span.parentId
      }
    }

  }

}
