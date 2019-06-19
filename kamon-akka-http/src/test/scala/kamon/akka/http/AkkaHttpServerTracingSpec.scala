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

package kamon.akka.http

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import kamon.testkit._
import kamon.tag.Lookups.{plain, plainLong, plainBoolean}
import kamon.trace.Span.Mark
import kamon.trace.Span
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.duration.{span, _}

class AkkaHttpServerTracingSpec extends WordSpecLike with Matchers with ScalaFutures with Inside with BeforeAndAfterAll
    with MetricInspection.Syntax with Reconfigure with TestWebServer with Eventually with OptionValues with TestSpanReporter {

  import TestWebServer.Endpoints._

  implicit private val system = ActorSystem("http-server-instrumentation-spec")
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val timeoutTest: FiniteDuration = 5 second
  val interface = "127.0.0.1"
  val port = 8081
  val webServer = startServer(interface, port)

  "the Akka HTTP server instrumentation" should {
    "create a server Span when receiving requests" in {
      val target = s"http://$interface:$port/$dummyPathOk"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "akka.http.server"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
      }
    }

    "not include variables in operation name" when {
       "including nested directives" in {
         val path = s"extraction/nested/42/fixed/anchor/32/${UUID.randomUUID().toString}/fixed/44/CafE"
         val expected = "/extraction/nested/{}/fixed/anchor/{}/{}/fixed/{}/{}"
         val target = s"http://$interface:$port/$path"
         Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

         eventually(timeout(10 seconds)) {
           val span = testSpanReporter().nextSpan().value
           span.operationName shouldBe expected
         }
       }

      "take a sampling decision when the routing tree hits an onComplete directive" in {
        val path = "extraction/on-complete/42/more-path"
        val expected = "/extraction/on-complete/{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }

      "take a sampling decision when the routing tree hits an onSuccess directive" in {
        val path = "extraction/on-success/42/after"
        val expected = "/extraction/on-success/{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }

      "take a sampling decision when the routing tree hits a completeOrRecoverWith directive with a failed future" in {
        val path = "extraction/complete-or-recover-with/42/after"
        val expected = "/extraction/complete-or-recover-with/{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }

      "take a sampling decision when the routing tree hits a completeOrRecoverWith directive with a successful future" in {
        val path = "extraction/complete-or-recover-with-success/42/after"
        val expected = "/extraction/complete-or-recover-with-success/{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }

      "including ambiguous nested directives" in {
        val path = s"v3/user/3/post/3"
        val expected = "/v3/user/{}/post/{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }
    }

    "change the Span operation name when using the operationName directive" in {
      val target = s"http://$interface:$port/$traceOk"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

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
      val target = s"http://$interface:$port/$dummyPathError"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

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
      val target = s"http://$interface:$port/unknown-path"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

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
      val target = s"http://$interface:$port/$stream"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      val span = eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/stream"
        span
      }

      inside(span.marks){
        case List(m2 @ Mark(_, "http.response.ready")) =>
      }

      span.tags.get(plain("http.url")) shouldBe target
      span.metricTags.get(plain("component")) shouldBe "akka.http.server"
      span.metricTags.get(plain("http.method")) shouldBe "GET"
    }

  }

  override protected def afterAll(): Unit = {
    webServer.shutdown()
  }


}

