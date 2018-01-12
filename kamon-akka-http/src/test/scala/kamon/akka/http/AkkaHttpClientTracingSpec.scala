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
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import kamon.Kamon
import kamon.context.{Key, TextMap}
import kamon.testkit._
import kamon.trace.{Span, SpanCustomizer}
import kamon.trace.Span.TagValue
import kamon.util.Registration
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpecLike}
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.duration._

class AkkaHttpClientTracingSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with MetricInspection
    with Reconfigure with TestWebServer with Eventually with OptionValues {

  import TestWebServer.Endpoints._

  implicit private val system = ActorSystem("http-client-instrumentation-spec")
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  val timeoutTest: FiniteDuration = 5 second
  val interface = "127.0.0.1"
  val port = 8080
  val webServer = startServer(interface, port)

  "the Akka HTTP client instrumentation" should {
    "create a client Span when using the request level API - Http().singleRequest(...)" in {
      val target = s"http://$interface:$port/$dummyPathOk"
      Http().singleRequest(HttpRequest(uri = target))

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe s"client /$dummyPathOk"
        spanTags("component") shouldBe "akka.http.client"
        spanTags("span.kind") shouldBe "client"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target

      }
    }

    "pick up customizations from the SpanCustomizer in context" in {
      val target = s"http://$interface:$port/$dummyPathOk"
      Kamon.withContextKey(SpanCustomizer.ContextKey, SpanCustomizer.forOperationName("get-dummy-path")) {
        Http().singleRequest(HttpRequest(uri = target))
      }

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe s"get-dummy-path"
        spanTags("component") shouldBe "akka.http.client"
        spanTags("span.kind") shouldBe "client"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target

      }
    }

    "serialize the current context into HTTP Headers" in {
      val target = s"http://$interface:$port/$replyWithHeaders"
      val broadcastKey = Key.broadcastString("custom-string-key")
      val broadcastValue = Some("Hello World :D")

      val response = Kamon.withContextKey(broadcastKey, broadcastValue) {
        Http().singleRequest(HttpRequest(uri = target, headers = List(RawHeader("X-Foo", "bar"))))
      }.flatMap(r => r.entity.toStrict(timeoutTest))

      eventually(timeout(10 seconds)) {
        val httpResponse = response.value.value.get
        val headersMap = parse(httpResponse.data.utf8String).extract[Map[String, String]]
        Kamon.contextCodec().HttpHeaders.decode(textMap(headersMap)).get(broadcastKey) shouldBe broadcastValue
        headersMap.keys.toList should contain allOf(
          "X-Foo",
          "X-B3-TraceId",
          "X-B3-SpanId",
          "X-B3-Sampled"
        )
      }
    }

    "mark Spans as errors if the client request failed" in {
      val target = s"http://$interface:$port/$dummyPathError"
      Http().singleRequest(HttpRequest(uri = target))

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe s"client /$dummyPathError"
        spanTags("component") shouldBe "akka.http.client"
        spanTags("span.kind") shouldBe "client"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target
        span.tags("error") shouldBe TagValue.True
        span.tags("http.status_code") shouldBe TagValue.Number(500)

      }
    }



    def stringTag(span: Span.FinishedSpan)(tag: String): String = {
      span.tags(tag).asInstanceOf[TagValue.String].string
    }

    def textMap(map: Map[String, String]): TextMap = new TextMap {
      override def values: Iterator[(String, String)] = map.iterator
      override def put(key: String, value: String): Unit = {}
      override def get(key: String): Option[String] = map.get(key)
    }
  }


  @volatile var registration: Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.addReporter(reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
    webServer.shutdown()
  }


}

