/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.okhttp3.instrumentation

import java.io.IOException

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import kamon.Kamon
import kamon.context.Context
import kamon.okhttp3.utils.{JettySupport, ServletTestSupport}
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, TestSpanReporter}
import kamon.trace.Span
import kamon.trace.SpanPropagation.B3
import okhttp3._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}

class OkHttpTracingInstrumentationSpec extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with BeforeAndAfterEach
    with TestSpanReporter
    with JettySupport
    with Reconfigure
    with OptionValues {

  val customTag = "requestId"
  val customHeaderName = "X-Request-Id"

  val uriError = "/path/fail"

  "the OkHttp Tracing Instrumentation" should {
    "propagate the current context and generate a span around an sync request" in {
      val okSpan = Kamon.spanBuilder("ok-sync-operation-span").start()
      val client = new OkHttpClient.Builder().build()
      val uri = "/path/sync"
      val url = s"http://$host:$port$uri"
      val request = new Request.Builder()
        .url(url)
        .build()

      Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        val response = client.newCall(request).execute()
        response.body().close()
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe url
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "okhttp-client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe url

        okSpan.id == span.parentId

        testSpanReporter().nextSpan() shouldBe None

        span
      }

      val requests = consumeSentRequests()

      requests.size should be(1)
      requests.head.uri should be(uri)
      requests.head.header(B3.Headers.TraceIdentifier).value should be(span.trace.id.string)
      requests.head.header(B3.Headers.SpanIdentifier).value should be(span.id.string)
      requests.head.header(B3.Headers.ParentSpanIdentifier).value should be(span.parentId.string)
      requests.head.header(B3.Headers.Sampled).value should be("1")
    }

    "propagate the current context and generate a span around an async request" in {
      val okAsyncSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      val client = new OkHttpClient.Builder().build()
      val uri = "/path/async"
      val url = s"http://$host:$port$uri"
      val request = new Request.Builder()
        .url(url)
        .build()

      Kamon.runWithContext(Context.of(Span.Key, okAsyncSpan)) {
        client.newCall(request).enqueue(new Callback() {
          override def onResponse(call: Call, response: Response): Unit = {}

          override def onFailure(call: Call, e: IOException): Unit =
            e.printStackTrace()
        })
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe url
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "okhttp-client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe url

        okAsyncSpan.id == span.parentId

        testSpanReporter().nextSpan() shouldBe None

        span
      }

      val requests = consumeSentRequests()

      requests.size should be(1)
      requests.head.uri should be(uri)
      requests.head.header(B3.Headers.TraceIdentifier).value should be(span.trace.id.string)
      requests.head.header(B3.Headers.SpanIdentifier).value should be(span.id.string)
      requests.head.header(B3.Headers.ParentSpanIdentifier).isEmpty should be(true)
      requests.head.header(B3.Headers.Sampled).value should be("1")
    }

    "propagate context tags" in {
      val okSpan = Kamon.internalSpanBuilder("ok-span-with-extra-tags", "user-app").start()
      val client = new OkHttpClient.Builder().build()
      val uri = "/path/sync/with-extra-tags"
      val url = s"http://$host:$port$uri"
      val request = new Request.Builder()
        .url(url)
        .build()

      Kamon.runWithContext(Context.of(Span.Key, okSpan).withTag(customTag, "1234")) {
        val response = client.newCall(request).execute()
        response.body().close()
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe url
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "okhttp-client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe url
        span.tags.get(plain(customTag)) shouldBe "1234"

        okSpan.id == span.parentId

        testSpanReporter().nextSpan() shouldBe None

        span
      }

      val requests = consumeSentRequests()

      requests.size should be(1)
      requests.head.uri should be(uri)
      requests.head.header(B3.Headers.TraceIdentifier).value should be(span.trace.id.string)
      requests.head.header(B3.Headers.SpanIdentifier).value should be(span.id.string)
      requests.head.header(B3.Headers.ParentSpanIdentifier).value should be(span.parentId.string)
      requests.head.header(B3.Headers.Sampled).value should be("1")
      requests.head.header(customHeaderName).value should be("1234")
    }

    "mark span as failed when server response with 5xx on sync execution" in {
      val okSpan = Kamon.spanBuilder("ok-sync-operation-span").start()
      val client = new OkHttpClient.Builder().build()
      val uri = uriError
      val url = s"http://$host:$port$uri"
      val request = new Request.Builder()
        .url(url)
        .build()

      Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        val response = client.newCall(request).execute()
        response.body().close()
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe url
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "okhttp-client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.tags.get(plain("http.url")) shouldBe url

        okSpan.id == span.parentId

        testSpanReporter().nextSpan() shouldBe None

        span
      }
      val requests = consumeSentRequests()

      requests.size should be(1)
      requests.head.uri should be(uri)
      requests.head.header(B3.Headers.TraceIdentifier).value should be(span.trace.id.string)
      requests.head.header(B3.Headers.SpanIdentifier).value should be(span.id.string)
      requests.head.header(B3.Headers.ParentSpanIdentifier).value should be(span.parentId.string)
      requests.head.header(B3.Headers.Sampled).value should be("1")
    }

    "mark span as failed when server response with 5xx on async execution" in {
      val okAsyncSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      val client = new OkHttpClient.Builder().build()
      val uri = uriError
      val url = s"http://$host:$port$uri"
      val request = new Request.Builder()
        .url(url)
        .build()

      Kamon.runWithContext(Context.of(Span.Key, okAsyncSpan)) {
        client.newCall(request).enqueue(new Callback() {
          override def onResponse(call: Call, response: Response): Unit = {}

          override def onFailure(call: Call, e: IOException): Unit =
            e.printStackTrace()
        })
      }

      val span: Span.Finished = eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe url
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "okhttp-client"
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.metricTags.get(plainBoolean("error")) shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        span.tags.get(plain("http.url")) shouldBe url

        okAsyncSpan.id == span.parentId

        testSpanReporter().nextSpan() shouldBe None

        span
      }
      val requests = consumeSentRequests()

      requests.size should be(1)
      requests.head.uri should be(uri)
      requests.head.header(B3.Headers.TraceIdentifier).value should be(span.trace.id.string)
      requests.head.header(B3.Headers.SpanIdentifier).value should be(span.id.string)
      requests.head.header(B3.Headers.ParentSpanIdentifier).isEmpty should be(true)
      requests.head.header(B3.Headers.Sampled).value should be("1")
    }
  }

  val servlet: ServletTestSupport = new HttpServlet() with ServletTestSupport {
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      addRequest(req)
      resp.addHeader("Content-Type", "text/plain")

      req.getRequestURI match {
        case path if path == uriError => resp.setStatus(500)
        case _                        => resp.setStatus(200)
      }
    }
  }

  override protected def beforeAll(): Unit = {
    Kamon.init()
    super.beforeAll()
    applyConfig(
      s"""
         |kamon {
         |  propagation.http.default.tags.mappings {
         |    $customTag = $customHeaderName
         |  }
         |  instrumentation.http-client.default.tracing.tags.from-context {
         |    $customTag = span
         |  }
         |}
         |""".stripMargin
    )

    startServer()
  }

  override protected def afterAll(): Unit = {
    stopServer()
    super.afterAll()
    Kamon.stop()
  }

  override protected def beforeEach(): Unit = {
    consumeSentRequests()
  }
}
