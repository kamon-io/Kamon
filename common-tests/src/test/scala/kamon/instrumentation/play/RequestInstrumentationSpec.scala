/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.play

import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups._
import kamon.testkit.{MetricInspection, TestSpanReporter}
import kamon.trace.Span
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.Handler.Stage
import play.api.mvc.Results.{NotFound, Ok}
import play.api.mvc._
import play.api.mvc.request.RequestAttrKey
import play.api.routing.{HandlerDef, Router}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.{ExecutionContext, Future}

class AkkaHTTPRequestHandlerInstrumentationSpec extends {
  val confFile = "/common-tests/src/test/resources/conf/application-akka-http.conf"
  //https://www.playframaework.com/documentation/2.6.x/NettyServer#Verifying-that-the-Netty-server-is-running
  //The Akka HTTP backend will not set a value for this request attribute.
  val expectedServer = "play.server.akka-http"
} with RequestHandlerInstrumentationSpec

class NettyRequestHandlerInstrumentationSpec extends {
  val confFile = "/common-tests/src/test/resources/conf/application-netty.conf"
  val expectedServer = "play.server.netty"
} with RequestHandlerInstrumentationSpec

abstract class RequestHandlerInstrumentationSpec extends PlaySpec with GuiceOneServerPerSuite with ScalaFutures
    with Eventually with SpanSugar with BeforeAndAfterAll with MetricInspection.Syntax with OptionValues with TestSpanReporter {

  val confFile: String
  val expectedServer: String

  System.setProperty("config.file", System.getProperty("user.dir") + confFile)
  
  implicit val executor: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def testRoutes(app: Application): PartialFunction[(String, String), Handler] = {
    val action = app.injector.instanceOf(classOf[DefaultActionBuilder])

    {
      case ("GET", "/ok")         => handler(action { Ok })
      case ("GET", "/request-id") => handler(action { Ok(Kamon.currentContext().getTag(option("request-id")).getOrElse("undefined")) })
      case ("GET", "/async")      => handler(action.async { Future { Ok } })
      case ("GET", "/not-found")  => handler(action { NotFound })
      case ("GET", "/server")     => handler(action { req => Ok(serverImplementationName(req)) })
      case ("GET", "/error")      => handler(action(_ => sys.error("This page generates an error!")))
    }
  }

  def serverImplementationName(req: Request[AnyContent]): String = {
    req.attrs.get(RequestAttrKey.Server)
      .map(s => if(s == "netty") "play.server.netty" else "unknown")
      .getOrElse("play.server.akka-http")
  }

  // Adds the HandlerDef attribute to the request which simulates what would happen when a generated router handles
  // the request.
  def handler[T](action: Action[T]): Handler = {
    Stage.modifyRequest(req => {
      req.addAttr(Router.Attrs.HandlerDef, HandlerDef(
        classLoader = getClass.getClassLoader,
        routerPackage = "kamon",
        controller = "kamon.TestController",
        method = "testMethod",
        parameterTypes = Seq.empty,
        verb = req.method,
        path = req.path
      ))
    }, action)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .appRoutes(testRoutes)
    .build

  "the Request instrumentation" should {
    "check the right server is configured" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/server"

      val response = await(wsClient.url(endpoint).get())
      response.status mustBe 200
      response.body mustBe expectedServer
    }

    "create server Spans for served operations" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/ok"
      val response = await(wsClient.url(endpoint).get())
      response.status mustBe 200

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName mustBe "/ok"
        span.metricTags.get(plain("component")) mustBe expectedServer
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L
      }
    }

    "read headers case-insensitively" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/request-id"

      {
        val response = await(wsClient.url(endpoint).addHttpHeaders("x-request-id" -> "123456").get())
        response.status mustBe 200
        response.body mustBe "123456"
      }
      {
        val response = await(wsClient.url(endpoint).addHttpHeaders("x-reQUest-Id" -> "654321").get())
        response.status mustBe 200
        response.body mustBe "654321"
      }
    }

    "mark spans as failed when there is an error processing the request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/error"
      val response = await(wsClient.url(endpoint).get())
      response.status mustBe 500

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName mustBe "/error"
        span.hasError mustBe true
        span.metricTags.get(plain("component")) mustBe expectedServer
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 500L
      }
    }

    "include the trace id in responses" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/ok"
      val parentSpan = Kamon.internalSpanBuilder("client-parent", "play.test").start()
      val response = Kamon.runWithSpan(parentSpan, finishSpan = false)(await(wsClient.url(endpoint).get()))
      response.status mustBe 200

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName mustBe "/ok"
        span.metricTags.get(plain("component")) mustBe expectedServer
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L

        span.trace.id mustBe parentSpan.trace.id
        response.header("trace-id").value mustBe parentSpan.trace.id.string
        response.header("span-id") mustBe empty
      }
    }
  }
}