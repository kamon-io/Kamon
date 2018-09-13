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
import kamon.Kamon
import kamon.context.{Context, Key}
import kamon.testkit._
import kamon.trace.Span.{Mark, TagValue}
import kamon.trace.{Span, SpanCustomizer}
import kamon.util.Registration
import org.json4s.native.JsonMethods.parse
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.duration.{span, _}

class AkkaHttpServerTracingSpec extends WordSpecLike
    with Matchers with ScalaFutures with Inside with BeforeAndAfterAll with MetricInspection
    with Reconfigure with TestWebServer with Eventually with OptionValues {

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
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe s"/${dummyPathOk}"
        spanTags("component") shouldBe "akka.http.server"
        spanTags("span.kind") shouldBe "server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target
        span.tags("http.status_code") shouldBe TagValue.Number(200)
      }
    }

    "not include variables in operation name" when {
       "including nested directives" in {
         val path = s"extraction/nested/42/fixed/anchor/32/${UUID.randomUUID().toString}/fixed/44/CafE"
         val expected = "/extraction/nested/{}/fixed/anchor/{}/{}/fixed/{}/{}"
         val target = s"http://$interface:$port/$path"
         Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())
         eventually(timeout(10 seconds)) {
           val span = reporter.nextSpan().value
           span.operationName shouldBe expected
         }
       }
      "including concatenated matchers" in {
        val path = s"extraction/concat/fixed${UUID.randomUUID().toString}CaFe"
        val expected = "/extraction/concat/fixed{}{}"
        val target = s"http://$interface:$port/$path"
        Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

        eventually(timeout(10 seconds)) {
          val span = reporter.nextSpan().value
          span.operationName shouldBe expected
        }
      }

    }

    //TODO decide what to do with operationName directive, currently whatever it sets gets overriden by instrumentation when route is completed
/*    "change the Span operation name when using the operationName directive" in {
      val target = s"http://$interface:$port/$traceOk"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "user-supplied-operation"
        spanTags("component") shouldBe "akka.http.server"
        spanTags("span.kind") shouldBe "server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target
        span.tags("http.status_code") shouldBe TagValue.Number(200)
      }
    }*/

    "mark spans as error when request fails" in {
      val target = s"http://$interface:$port/$dummyPathError"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe s"/$dummyPathError"
        spanTags("component") shouldBe "akka.http.server"
        spanTags("span.kind") shouldBe "server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target
        span.tags("http.status_code") shouldBe TagValue.Number(500)
      }
    }


    "change the operation name to 'unhandled' when the response status code is 404" in {
      val target = s"http://$interface:$port/unknown-path"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "unhandled"
        spanTags("component") shouldBe "akka.http.server"
        spanTags("span.kind") shouldBe "server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe target
        span.tags("http.status_code") shouldBe TagValue.Number(404)
      }
    }

    "deserialize the Context from HTTP Headers" in {
      val stringKey = Key.broadcastString("custom-string-key")
      val target = s"http://$interface:$port/$basicContext"
      val parentSpan = Kamon.buildSpan("parent").start()
      val context = Context.Empty
        .withKey(Span.ContextKey, parentSpan)
        .withKey(SpanCustomizer.ContextKey, SpanCustomizer.forOperationName("deserialize-context"))
        .withKey(stringKey, Some("hello for the server"))

      val response = Kamon.withContext(context) {
        Http().singleRequest(HttpRequest(uri = target))
          .flatMap(r => r.entity.toStrict(timeoutTest))
      }

      eventually(timeout(10 seconds)) {
        val httpResponse = response.value.value.get
        val basicContext = parse(httpResponse.data.utf8String).extract[Map[String, String]]

        basicContext("custom-string-key") shouldBe "hello for the server"
        basicContext("trace-id") shouldBe parentSpan.context().traceID.string
      }
    }


    "correctly time entity transfer timings" in {
      val target = s"http://$interface:$port/$stream"
      Http().singleRequest(HttpRequest(uri = target)).map(_.discardEntityBytes())

      val span = eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        span.operationName shouldBe "/stream"
        span
      }
      val spanTags = stringTag(span) _
      inside(span.marks){
        case List(m2@Mark(_, "response-ready")) =>

      }
      spanTags("component") shouldBe "akka.http.server"
      spanTags("span.kind") shouldBe "server"
      spanTags("http.method") shouldBe "GET"
      spanTags("http.url") shouldBe target
    }


    def stringTag(span: Span.FinishedSpan)(tag: String): String = {
      span.tags(tag).asInstanceOf[TagValue.String].string
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

