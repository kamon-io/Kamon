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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import kamon.Kamon
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.OptionValues

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class AkkaHttpClientTracingSpec extends AnyWordSpecLike with Matchers with InitAndStopKamonAfterAll with MetricInspection.Syntax
    with Reconfigure with TestWebServer with Eventually with OptionValues with TestSpanReporter {

  import TestWebServer.Endpoints._

  implicit private val system: ActorSystem = ActorSystem("http-client-instrumentation-spec")
  implicit private val executor: ExecutionContextExecutor = system.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  val timeoutTest: FiniteDuration = 5 second
  val interface = "127.0.0.1"
  val port = 8080
  val webServer = startServer(interface, port)

  "the Akka HTTP client instrumentation" should {
    "create a client Span when using the request level API - Http().singleRequest(...)" in {
      val target = s"http://$interface:$port/$dummyPathOk"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter.nextSpan().value
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "akka.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
      }
    }

    "create a client Span when using the request level API - Http().singleRequest(...) from Java" in {
      val target = s"http://$interface:$port/$dummyPathOk"

      val http = akka.http.javadsl.Http.get(system)
      http.singleRequest(HttpRequest(uri = target))

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter.nextSpan().value
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "akka.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
      }
    }

    "serialize the current context into HTTP Headers" in {
      val target = s"http://$interface:$port/$replyWithHeaders"
      val tagKey = "custom.message"
      val tagValue = "Hello World :D"

      val response = Kamon.runWithContextTag(tagKey, tagValue) {
        Http().singleRequest(HttpRequest(uri = target, headers = List(RawHeader("X-Foo", "bar"))))
      }.flatMap(r => r.entity.toStrict(timeoutTest))

      eventually(timeout(10 seconds)) {
        val httpResponse = response.value.value.get
        val headersMap = parse(httpResponse.data.utf8String).extract[Map[String, String]]

        headersMap.keys.toList should contain allOf(
          "context-tags",
          "X-Foo",
          "X-B3-TraceId",
          "X-B3-SpanId",
          "X-B3-Sampled"
        )

        headersMap.get("context-tags").value shouldBe "custom.message=Hello World :D;upstream.name=kamon-application;"
      }
    }

    "mark Spans as errors if the client request failed" in {
      val target = s"http://$interface:$port/$dummyPathError"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "GET"
        span.tags.get(plain("http.url")) shouldBe target
        span.metricTags.get(plain("component")) shouldBe "akka.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.hasError shouldBe true
      }
    }

    "keep operation names provided by the HTTP Client instrumentation" in {
      val target = s"http://$interface:$port/name-will-be-changed"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "named-via-config"
      }
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    webServer.shutdown()
  }
}

