/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.play.instrumentation

import kamon.Kamon
import kamon.play.{ Play, PlayExtension }
import kamon.trace.{ TraceContextAware, TraceRecorder }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import play.api.mvc._
import play.libs.Akka

@Aspect
class RequestInstrumentation {

  @DeclareMixin("play.api.mvc.RequestHeader+")
  def mixinContextAwareNewRequest: TraceContextAware = TraceContextAware.default

  @After("execution(* play.api.GlobalSettings+.onStart(*)) && args(application)")
  def afterApplicationStart(application: play.api.Application): Unit = {
    Kamon(Play)(Akka.system())
  }

  @Before("execution(* play.api.GlobalSettings+.onRouteRequest(..)) && args(requestHeader)")
  def onRouteRequest(requestHeader: RequestHeader): Unit = {
    val system = Akka.system()
    val playExtension = Kamon(Play)(system)
    val defaultTraceName: String = s"${requestHeader.method}: ${requestHeader.uri}"

    val token = if (playExtension.includeTraceToken) {
      requestHeader.headers.toSimpleMap.find(_._1 == playExtension.traceTokenHeaderName).map(_._2)
    } else None

    TraceRecorder.start(defaultTraceName, token)(system)
  }

  @Around("execution(* play.api.GlobalSettings+.doFilter(*)) && args(next)")
  def aroundDoFilter(pjp: ProceedingJoinPoint, next: EssentialAction): Any = {
    val essentialAction = (requestHeader: RequestHeader) ⇒ {

      val incomingContext = TraceRecorder.currentContext
      val executor = Kamon(Play)(Akka.system()).defaultDispatcher

      next(requestHeader).map {
        result ⇒
          TraceRecorder.finish()

          incomingContext.map { ctx ⇒
            val playExtension = Kamon(Play)(ctx.system)
            recordHttpServerMetrics(result, ctx.name, playExtension)
            if (playExtension.includeTraceToken) result.withHeaders(playExtension.traceTokenHeaderName -> ctx.token)
            else result
          }.getOrElse(result)
      }(executor)
    }
    pjp.proceed(Array(EssentialAction(essentialAction)))
  }

  private def recordHttpServerMetrics(result: Result, traceName: String, playExtension: PlayExtension): Unit =
    playExtension.httpServerMetrics.recordResponse(traceName, result.header.status.toString, 1L)

  @Around("execution(* play.api.GlobalSettings+.onError(..)) && args(request, ex)")
  def aroundOnError(pjp: ProceedingJoinPoint, request: TraceContextAware, ex: Throwable): Any = request.traceContext match {
    case None ⇒ pjp.proceed()
    case Some(ctx) ⇒ {
      val actorSystem = ctx.system
      Kamon(Play)(actorSystem).publishErrorMessage(actorSystem, ex.getMessage, ex)
      pjp.proceed()
    }
  }
}
