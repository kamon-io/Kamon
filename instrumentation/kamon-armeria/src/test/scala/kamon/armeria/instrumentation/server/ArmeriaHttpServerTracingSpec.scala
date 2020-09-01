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

package kamon.armeria.instrumentation.server

import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit._
import okhttp3.{OkHttpClient, Request}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import utils.ArmeriaServerSupport.startArmeriaServer
import utils.TestEndpoints._

import scala.concurrent.duration._

class ArmeriaHttpServerTracingSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with Eventually
  with TestSpanReporter {

  private val okHttp = new OkHttpClient.Builder().build()

  val interface = "127.0.0.1"
  val httpPort = 8081

  private val httpServer = startArmeriaServer(httpPort)

  testSuite("http", interface, httpPort)

  private def testSuite(protocol: String, interface: String, port: Int): Unit = {

    s"The Armeria $protocol server" should {

      "create a server Span when receiving requests" in {
        val target = s"$protocol://$interface:$port/$dummyPath"
        val expected = "dummy.get"
        okHttp.newCall(new Request.Builder().url(target).get().build()).execute().close()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "armeria-http-server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
        }
      }

      "set operation name with unhandled" when {
        "request path doesn't exists" in {
          val target = s"$protocol://$interface:$port/$dummyNotFoundPath"
          val expected = "unhandled"
          okHttp.newCall(new Request.Builder().url(target).get().build()).execute().close()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            println(span.operationName)
            span.operationName shouldBe expected
            span.tags.get(plain("http.url")) shouldBe target
            span.metricTags.get(plain("component")) shouldBe "armeria-http-server"
            span.metricTags.get(plain("http.method")) shouldBe "GET"
            span.metricTags.get(plainBoolean("error")) shouldBe false
            span.metricTags.get(plainLong("http.status_code")) shouldBe 404
          }
        }
      }

      "set operation name with path + http method" when {
        "resource doesn't exist" in {
          val target = s"$protocol://$interface:$port/$dummyResourceNotFoundPath"
          val expected = "dummy-resource-not-found.get"
          okHttp.newCall(new Request.Builder().url(target).get().build()).execute().close()

          eventually(timeout(10 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
            span.tags.get(plain("http.url")) shouldBe target
            span.metricTags.get(plain("component")) shouldBe "armeria-http-server"
            span.metricTags.get(plain("http.method")) shouldBe "GET"
            span.metricTags.get(plainBoolean("error")) shouldBe false
            span.metricTags.get(plainLong("http.status_code")) shouldBe 404
          }
        }
      }

      "not include path variables names" in {
        val target = s"$protocol://$interface:$port/$dummyMultipleResourcesPath"
        val expected = "dummy-resources/{}/other-resources/{}"
        okHttp.newCall(new Request.Builder().url(target).get().build()).execute().close()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
        }
      }

      "not fail when request url contains special regexp chars" in {
        val target = s"$protocol://$interface:$port/$dummyMultipleResourcesPath**"
        val expected = "dummy-resources/{}/other-resources/{}"
        val response = okHttp.newCall(new Request.Builder().url(target).build()).execute()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.operationName shouldBe expected
          response.code() shouldBe 200
        }
        response.close()
      }

      "mark spans as failed when request fails" in {
        val target = s"$protocol://$interface:$port/$dummyErrorPath"
        val expected = s"$dummyErrorPath.get"
        okHttp.newCall(new Request.Builder().url(target).build()).execute().close()

        eventually(timeout(10 seconds)) {
          val span = testSpanReporter().nextSpan().value
          span.tags.get(plain("http.url")) shouldBe target
          span.metricTags.get(plain("component")) shouldBe "armeria-http-server"
          span.metricTags.get(plain("http.method")) shouldBe "GET"
          span.metricTags.get(plainBoolean("error")) shouldBe true
          span.metricTags.get(plainLong("http.status_code")) shouldBe 500
        }
      }
    }
  }

  override protected def afterAll(): Unit =
    httpServer.close()
}
