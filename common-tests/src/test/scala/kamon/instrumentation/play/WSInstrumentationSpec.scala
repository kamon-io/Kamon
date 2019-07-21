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
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit._
import kamon.trace.Span
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.mvc.Results.{InternalServerError, NotFound, Ok}
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, Handler}
import play.api.test.Helpers._
import play.api.test._
import play.api.libs.ws.{WSRequestExecutor, WSRequestFilter}
import play.api.mvc.Handler.Stage
import play.api.routing.{HandlerDef, Router}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class WSInstrumentationSpec extends PlaySpec with GuiceOneServerPerSuite with ScalaFutures with Eventually with SpanSugar
  with BeforeAndAfterAll with MetricInspection.Syntax with Reconfigure with OptionValues with TestSpanReporter {

  System.setProperty("config.file", "./common-tests/src/test/resources/conf/application.conf")

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .appRoutes(testRoutes)
    .build

  def testRoutes(app: Application): PartialFunction[(String, String), Handler] = {
    val action = app.injector.instanceOf(classOf[DefaultActionBuilder])

    {
      case ("GET", "/ok")                 => handler(action { Ok })
      case ("GET", "/trace-id")           => handler(action { Ok(Kamon.currentSpan().trace.id.string) })
      case ("GET", "/example-tag")        => handler(action { Ok(Kamon.currentContext().getTag(plain("example"))) })
      case ("GET", "/error")              => handler(action { InternalServerError })
      case ("GET", "/inside-controller")  => handler(insideController(s"http://localhost:$port/async")(app))
    }
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

  "the WS instrumentation" should {
    "generate a client span for the WS request" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val endpoint = s"http://localhost:$port/ok"
      val response = await(wsClient.url(endpoint).get())
      response.status mustBe 200

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.kind mustBe Span.Kind.Client
        span.operationName mustBe "GET"
        span.metricTags.get(plain("component")) mustBe "play.http.client"
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L
      }
    }

    "ensure that server Span has the same trace ID as the client Span" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val parentSpan = Kamon.internalSpanBuilder("inside-controller-operation-span", "test").start()
      val endpoint = s"http://localhost:$port/trace-id"

      val response = Kamon.runWithSpan(parentSpan)(await(wsClient.url(endpoint).get()))
      response.status mustBe 200

      eventually(timeout(2 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.kind mustBe Span.Kind.Client
        span.operationName mustBe "GET"
        span.metricTags.get(plain("component")) mustBe "play.http.client"
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L

        response.body mustBe parentSpan.trace.id.string
      }
    }

    "propagate context tags" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val testContext = Context.of("example", "one")
      val endpoint = s"http://localhost:$port/example-tag"

      val response = Kamon.runWithContext(testContext)(await(wsClient.url(endpoint).get()))
      response.status mustBe 200
      response.body mustBe "one"
    }

    "run the WSClient instrumentation only once, even if request filters are added" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val okSpan = Kamon.internalSpanBuilder("ok-operation-span", "test").start()
      val endpoint = s"http://localhost:$port/ok"

      Kamon.runWithSpan(okSpan) {
        val response = await(wsClient.url(endpoint)
          .withRequestFilter(new DumbRequestFilter())
          .get())

        response.status mustBe 200
      }

      eventually(timeout(2 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.kind mustBe Span.Kind.Client
        span.operationName mustBe "GET"
        span.metricTags.get(plain("component")) mustBe "play.http.client"
        span.metricTags.get(plain("http.method")) mustBe "GET"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L
      }
    }
  }

  def insideController(url: String)(app:Application): Action[AnyContent] = {
    val action = app.injector.instanceOf(classOf[DefaultActionBuilder])
    val wsClient = app.injector.instanceOf[WSClient]

    action.async {
      wsClient.url(url).get().map(_ => Ok("Ok"))
    }
  }

  class DumbRequestFilter() extends WSRequestFilter {
    def apply(executor: WSRequestExecutor) = WSRequestExecutor { request =>
      executor(request) andThen {
        case Success(_) =>
        case Failure(_) =>
      }
    }
  }
}


