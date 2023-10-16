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

package kamon.instrumentation.armeria.server

import com.linecorp.armeria.client.{ClientFactory, Clients, WebClient}
import com.linecorp.armeria.common.{HttpMethod, HttpRequest, RequestHeaders}
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit._
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import utils.ArmeriaServerSupport.startArmeriaServer
import utils.Endpoints._

import scala.concurrent.duration._

class ArmeriaHttpServerTracingSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with InitAndStopKamonAfterAll
  with Eventually
  with TestSpanReporter {

  val interface = "127.0.0.1"
  val httpPort = 8080
  val httpsPort = 8081

  private val httpServer = startArmeriaServer(httpPort, httpsPort = Some(httpsPort))

  testSuite("http", interface, httpPort)
  testSuite("https", interface, httpsPort)

  private def testSuite(protocol: String, interface: String, port: Int): Unit = {

    val webClient = newWebClient(protocol, port)

    s"The Armeria $protocol server" when {

      "Non blocking route" should {

        "create a server Span when receiving requests" in {
          val target = s"$protocol://$interface:$port/$usersEndpoint"
          val expected = "/users"

          val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, usersEndpoint))
          webClient.execute(request)

          eventually(timeout(3 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
            span.tags.get(plain("http.url")) shouldBe target
            span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
            span.metricTags.get(plain("http.method")) shouldBe "GET"
            span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
          }
        }

        "set operation name with unhandled" when {
          "request path doesn't exists" in {
            val target = s"$protocol://$interface:$port/$pathNotFoundEndpoint"
            val expected = "unhandled"

            val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, pathNotFoundEndpoint))
            webClient.execute(request)

            eventually(timeout(3 seconds)) {
              val span = testSpanReporter().nextSpan().value
              span.operationName shouldBe expected
              span.tags.get(plain("http.url")) shouldBe target
              span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
              span.metricTags.get(plain("http.method")) shouldBe "GET"
              span.metricTags.get(plainBoolean("error")) shouldBe false
              span.metricTags.get(plainLong("http.status_code")) shouldBe 404
            }
          }
        }

        "set operation name with path + http method" when {
          "resource doesn't exist" in {
            val target = s"$protocol://$interface:$port/$usersEndpoint/not-found"
            val expected = "/users/:userId"

            val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, s"$usersEndpoint/not-found"))
            webClient.execute(request)

            eventually(timeout(3 seconds)) {
              val span = testSpanReporter().nextSpan().value
              span.operationName shouldBe expected
              span.tags.get(plain("http.url")) shouldBe target
              span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
              span.metricTags.get(plain("http.method")) shouldBe "GET"
              span.metricTags.get(plainBoolean("error")) shouldBe false
              span.metricTags.get(plainLong("http.status_code")) shouldBe 404
            }
          }
        }

        "not include path variables names" in {
          val expected = "/users/:userId/accounts/:accountId"

          val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, userAccountEndpoint))
          webClient.execute(request)

          eventually(timeout(3 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
          }
        }

        "not fail when request url contains special regexp chars" in {
          val expected = "/users/:userId/accounts/:accountId"

          val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, s"$userAccountEndpoint**"))
          val response = webClient.execute(request).aggregate().get()

          eventually(timeout(3 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
            response.status().code() shouldBe 200
          }
        }

        "mark spans as failed when request fails" in {
          val target = s"$protocol://$interface:$port/$usersEndpoint/error"
          val expected = "/users/:userId"

          val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, s"$usersEndpoint/error"))
          webClient.execute(request)

          eventually(timeout(3 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
            span.tags.get(plain("http.url")) shouldBe target
            span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
            span.metricTags.get(plain("http.method")) shouldBe "GET"
            span.metricTags.get(plainBoolean("error")) shouldBe true
            span.metricTags.get(plainLong("http.status_code")) shouldBe 500
          }
        }

        "return a redirect status code" when {
          "a request to /docs is redirected to /docs/" in {
            val target = s"$protocol://$interface:$port/$docsEndpoint"
            val expected = s"/docs/*"

            val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, s"$docsEndpoint"))
            webClient.execute(request)

            eventually(timeout(3 seconds)) {
              val span = testSpanReporter().nextSpan().value
              span.operationName shouldBe expected
              span.tags.get(plain("http.url")) shouldBe target
              span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
              span.metricTags.get(plain("http.method")) shouldBe "GET"
              span.metricTags.get(plainLong("http.status_code")) shouldBe 307L
            }
          }
        }

        "return a ok status code" when {
          "a request to /docs/ is done" in {
            val target = s"$protocol://$interface:$port/$docsEndpoint/"
            val expected = s"/docs/*"

            val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, s"$docsEndpoint/"))
            webClient.execute(request)

            eventually(timeout(3 seconds)) {
              val span = testSpanReporter().nextSpan().value
              span.operationName shouldBe expected
              span.tags.get(plain("http.url")) shouldBe target
              span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
              span.metricTags.get(plain("http.method")) shouldBe "GET"
              span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
            }
          }
        }
      }

      "Blocking route" should {

        "create a server Span when receiving requests" in {
          val target = s"$protocol://$interface:$port/$usersBlockingEndpoint"
          val expected = "/users-blocking"

          val request = HttpRequest.of(RequestHeaders.of(HttpMethod.GET, usersBlockingEndpoint))
          webClient.execute(request)

          eventually(timeout(3 seconds)) {
            val span = testSpanReporter().nextSpan().value
            span.operationName shouldBe expected
            span.tags.get(plain("http.url")) shouldBe target
            span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
            span.metricTags.get(plain("http.method")) shouldBe "GET"
            span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
          }
        }
      }
    }
  }

  private def newWebClient(protocol: String, port: Int): WebClient = {
    val clientFactory = ClientFactory.builder().tlsNoVerifyHosts("localhost", "127.0.0.1").build()
    val webClient = Clients.builder(s"$protocol://$interface:$port").build(classOf[WebClient])
    clientFactory.newClient(webClient).asInstanceOf[WebClient]
  }

  override protected def afterAll(): Unit = {
    httpServer.close()
    super.afterAll()
  }
}
