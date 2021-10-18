/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <https://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
*/

package kamon.akka.http

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import javax.net.ssl.{HostnameVerifier, SSLSession}
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit._
import kamon.trace.Span.Mark
import okhttp3.{OkHttpClient, Request}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.duration._

class AkkaHttpServerTracingSpec extends WordSpecLike with Matchers with ScalaFutures with Inside with InitAndStopKamonAfterAll
    with MetricInspection.Syntax with Reconfigure with TestWebServer with Eventually with OptionValues with TestSpanReporter {

  import TestWebServer.Endpoints._

  implicit private val system = ActorSystem("http-server-instrumentation-spec")
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val (sslSocketFactory, trustManager) = clientSSL()
  val okHttp = new OkHttpClient.Builder()
    .sslSocketFactory(sslSocketFactory, trustManager)
    .hostnameVerifier(new HostnameVerifier { override def verify(s: String, sslSession: SSLSession): Boolean = true })
    .build()

  val timeoutTest: FiniteDuration = 5 second
  val interface = "127.0.0.1"
  val http1WebServer = startServer(interface, 8081, https = false)
  val http2WebServer = startServer(interface, 8082, https = true)

  testSuite("HTTP/1", http1WebServer)
  testSuite("HTTP/2", http2WebServer)

  def testSuite(httpVersion: String, server: WebServer) = {
    val interface = server.interface
    val port = server.port
    val protocol = server.protocol

    s"the Akka HTTP server instrumentation with ${httpVersion}" should {
      "create a server Span when receiving requests" in {
        val target = s"$protocol://$interface:$port/$dummyPathOk"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "akka.http.server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
        }
      }

      "return the correct operation name with overloaded route" in {
        val target = s"$protocol://$interface:$port/some_endpoint"

        // this endpoint isn't affected
//        okHttp.newCall(new Request.Builder()
//          .post(RequestBody.create(MediaType.get("application/json"), "{}"))
//          .url(target).build())
//          .execute()

        okHttp.newCall(new Request.Builder()
          .get()
          .url(target).build())
          .execute()

        val span = eventually(timeout(10 seconds))(testSpanReporter().nextSpan().value)
        span.operationName shouldBe "/some_endpoint"
      }
      "not include variables in operation name" when {
        "including nested directives" in {
          val path = s"extraction/nested/42/fixed/anchor/32/${UUID.randomUUID().toString}/fixed/44/CafE"
          val expected = "/extraction/nested/{}/fixed/anchor/{}/{}/fixed/{}/{}"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "not fail when request url contains special regexp chars" in {
          val path = "extraction/segment/special**"
          val expected = "/extraction/segment/{}"
          val target = s"$protocol://$interface:$port/$path"
          val response = okHttp.newCall(new Request.Builder().url(target).build()).execute()

          response.code() shouldBe 200
          response.body().string() shouldBe "special**"

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "take a sampling decision when the routing tree hits an onComplete directive" in {
          val path = "extraction/on-complete/42/more-path"
          val expected = "/extraction/on-complete/{}/more-path"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "take a sampling decision when the routing tree hits an onSuccess directive" in {
          val path = "extraction/on-success/42/after"
          val expected = "/extraction/on-success/{}/after"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "take a sampling decision when the routing tree hits a completeOrRecoverWith directive with a failed future" in {
          val path = "extraction/complete-or-recover-with/42/after"
          val expected = "/extraction/complete-or-recover-with/{}/after"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "take a sampling decision when the routing tree hits a completeOrRecoverWith directive with a successful future" in {
          val path = "extraction/complete-or-recover-with-success/42/after"
          val expected = "/extraction/complete-or-recover-with-success/{}"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "including ambiguous nested directives" in {
          val path = s"v3/user/3/post/3"
          val expected = "/v3/user/{}/post/{}"
          val target = s"$protocol://$interface:$port/$path"
          okHttp.newCall(new Request.Builder().url(target).build()).execute()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }
      }

      "change the Span operation name when using the operationName directive" in {
        val target = s"$protocol://$interface:$port/$traceOk"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe "user-supplied-operation"
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "akka.http.server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
        }
      }

      "mark spans as failed when request fails" in {
        val target = s"$protocol://$interface:$port/$dummyPathError"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe s"/$dummyPathError"
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "akka.http.server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainBoolean("error")) shouldBe true
          span.metricTags.get(plainLong("http.status_code")) shouldBe 500L
        }
      }

      "change the operation name to 'unhandled' when the response status code is 404" in {
        val target = s"$protocol://$interface:$port/unknown-path"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe "unhandled"
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "akka.http.server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainBoolean("error")) shouldBe false
          span.metricTags.get(plainLong("http.status_code")) shouldBe 404L
        }
      }

      "correctly time entity transfer timings" in {
        val target = s"$protocol://$interface:$port/$stream"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        val span = eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe "/stream"
          span
        }

        inside(span.marks){
          case List(_ @ Mark(_, "http.response.ready")) =>
        }

        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "akka.http.server"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
      }

      "include the trace-id and keep all user-provided headers in the responses" in {
        val target = s"$protocol://$interface:$port/extra-header"
        val response = okHttp.newCall(new Request.Builder().url(target).build()).execute()

        response.headers().names() should contain allOf (
          "trace-id",
          "extra"
        )
      }

      "keep operation names provided by the HTTP Server instrumentation" in {
        val target = s"$protocol://$interface:$port/name-will-be-changed"
        okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe "named-via-config"
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    http1WebServer.shutdown()
    http2WebServer.shutdown()
  }
}

