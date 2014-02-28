/* ===================================================
 * Copyright © 2013 2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package kamon.play.instrumentation

import org.aspectj.lang.annotation._
import play.api.mvc._
import kamon.trace.{ TraceRecorder, TraceContextAware }
import kamon.Kamon
import kamon.play.Play
import akka.actor.ActorSystem
import play.libs.Akka
import scala.Some
import org.aspectj.lang.ProceedingJoinPoint

@Aspect
class RequestInstrumentation {

  @DeclareMixin("play.api.mvc.Request || play.api.mvc.WrappedRequest || play.api.test.FakeRequest")
  def mixinContextAwareNewRequest: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(* play.api.GlobalSettings+.onStart(*)) && args(application)")
  def onStart(application: play.api.Application): Unit = {}

  @After("onStart(application)")
  def afterApplicationStart(application: play.api.Application): Unit = {
    Kamon(Play)(Akka.system())
  }

  @Pointcut("execution(* play.api.GlobalSettings+.onRouteRequest(*)) && args(request)")
  def onRouteRequest(request: TraceContextAware): Unit = {}

  @After("onRouteRequest(request)")
  def afterRouteRequest(request: TraceContextAware): Unit = {
    val system: ActorSystem = Akka.system()
    val playExtension = Kamon(Play)(system)

    val requestHeader: RequestHeader = request.asInstanceOf[RequestHeader]
    val defaultTraceName: String = requestHeader.method + ": " + requestHeader.uri
    val token = if (playExtension.includeTraceToken) {
      requestHeader.headers.toSimpleMap.find(_._1 == playExtension.traceTokenHeaderName).map(_._2)
    } else None

    TraceRecorder.start(defaultTraceName, token)(system)

    //Necessary to force initialization of traceContext when initiating the request.
    request.traceContext
  }

  @Pointcut("execution(* play.api.GlobalSettings+.doFilter(*)) && args(next)")
  def doFilter(next: EssentialAction): Unit = {}

  @Around("doFilter(next)")
  def afterDoFilter(pjp: ProceedingJoinPoint, next: EssentialAction): Any = {
    Filters(pjp.proceed(Array(next)).asInstanceOf[EssentialAction], kamonRequestFilter)
  }

  private[this] val kamonRequestFilter = Filter { (nextFilter, requestHeader) ⇒
    import scala.concurrent.ExecutionContext.Implicits.global

    val incomingContext = TraceRecorder.currentContext

    nextFilter(requestHeader).map { result ⇒
      TraceRecorder.finish()

      val simpleResult = incomingContext match {
        case None ⇒ result
        case Some(traceContext) ⇒ {
          val playExtension = Kamon(Play)(traceContext.system)
          if (playExtension.includeTraceToken) {
            result.withHeaders(playExtension.traceTokenHeaderName -> traceContext.token)
          } else result
        }
      }
      simpleResult
    }
  }
}
