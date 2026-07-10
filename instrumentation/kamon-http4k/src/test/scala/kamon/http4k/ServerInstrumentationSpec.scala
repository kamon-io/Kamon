/*
 * =========================================================================================
 * Copyright © 2013-2024 the kamon project <http://kamon.io/>
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

package kamon.http4k

import kamon.Kamon
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import kamon.trace.Span
import org.http4k.client.URLConnectionHttpClient
import org.http4k.core.{Http4kKt, Method, Request, Response, Status}
import org.http4k.routing.{HttpKt, PathMethod}
import org.http4k.server.{ServerConfig, SunHttp}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import org.scalatest.BeforeAndAfterEach
import kotlin.jvm.functions.Function1

class ServerInstrumentationSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with OptionValues
    with TestSpanReporter
    with InitAndStopKamonAfterAll
    with BeforeAndAfterEach {

  type HttpHandler = Function1[Request, Response]

  override def beforeEach(): Unit = {
    testSpanReporter().clear()
    super.beforeEach()
  }

  // A simple http4k app with a handful of routes
  val testRoutes = HttpKt.routes(
    new PathMethod("/tracing/ok", Method.GET).to(
      new HttpHandler {
        override def invoke(req: Request): Response = Response.create(Status.OK).body("ok")
      }
    ),
    new PathMethod("/tracing/not-found", Method.GET).to(
      new HttpHandler {
        override def invoke(req: Request): Response = Response.create(Status.NOT_FOUND)
      }
    ),
    new PathMethod("/tracing/error", Method.GET).to(
      new HttpHandler {
        override def invoke(req: Request): Response = Response.create(Status.INTERNAL_SERVER_ERROR)
      }
    ),
    new PathMethod("/tracing/ok", Method.POST).to(
      new HttpHandler {
        override def invoke(req: Request): Response = Response.create(Status.OK)
      }
    ),
    new PathMethod("/tracing/something/ok", Method.GET).to(
      new HttpHandler {
        override def invoke(req: Request): Response = Response.create(Status.OK).body("ok something")
      }
    )
  )

  val interface = "127.0.0.1"
  val port = 43568

  // Wrap with Kamon instrumentation. KamonFilter is backend-agnostic
  val app = Http4kKt.then(KamonFilter(interface, port), testRoutes)

  // Start a SunHttp server (built-in JDK server)
  val server = new SunHttp(port).toServer(app).start()

  val client = URLConnectionHttpClient.create()

  override def afterAll(): Unit = {
    server.stop()
    super.afterAll()
  }

  private def get(path: String): Response =
    client.invoke(Request.create(Method.GET, s"http://$interface:$port$path"))

  "The KamonFilter server instrumentation" should {

    "create a span for a successful request" in {
      val okSpan = Kamon.spanBuilder("parent-span").start()

      Kamon.runWithSpan(okSpan) {
        get("/tracing/ok")
      }

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/tracing/ok"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe "http4k.server"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.hasError shouldBe false
      }
    }

    "apply glob operation name mappings from config" in {
      // application.conf maps "/tracing/*/ok" -> "/tracing/:name/ok"
      get("/tracing/something/ok")

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/tracing/:name/ok"
      }
    }

    "mark a span as failed for a 5xx response" in {
      get("/tracing/error")

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.hasError shouldBe true
      }
    }

    "create a span for a POST request" in {
      client.invoke(Request.create(Method.POST, s"http://$interface:$port/tracing/ok"))

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.metricTags.get(plain("http.method")) shouldBe "POST"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
      }
    }

    "propagate an incoming trace context from request headers" in {
      val parentSpan = Kamon.spanBuilder("remote-parent").start()
      val ctx = Kamon.currentContext().withEntry(Span.Key, parentSpan)

      var request = Request.create(Method.GET, s"http://$interface:$port/tracing/ok")
      Kamon.defaultHttpPropagation().write(ctx, (h, v) => request = request.header(h, v))

      // Make the request. The server filter should re-attach to the parent
      client.invoke(request)
      parentSpan.finish()

      eventually(timeout(3 seconds)) {
        val spans = testSpanReporter().spans()
        val serverSpan = spans.find(_.kind == Span.Kind.Server)
        serverSpan.value.parentId shouldBe parentSpan.id
      }
    }

    "use the unhandled operation name for unmatched routes" in {
      get("/does/not/exist")

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "unhandled"
      }
    }
  }
}
