/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import kamon.play.PlayExtension
import kamon.trace._
import kamon.util.SameThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import play.api.mvc.Results._
import play.api.mvc._

@Aspect
class RequestInstrumentation {

  @DeclareMixin("play.api.mvc.RequestHeader+")
  def mixinContextAwareNewRequest: TraceContextAware = TraceContextAware.default

  @Before("call(* play.api.GlobalSettings.onRouteRequest(..)) && args(requestHeader)")
  def beforeRouteRequest(requestHeader: RequestHeader): Unit = {
    import Kamon.tracer

    val token = if (PlayExtension.includeTraceToken) {
      requestHeader.headers.get(PlayExtension.traceTokenHeaderName)
    } else None

    Tracer.setCurrentContext(tracer.newContext("UnnamedTrace", token))
  }

  @Around("call(* play.api.GlobalSettings.doFilter(*)) && args(next)")
  def aroundDoFilter(pjp: ProceedingJoinPoint, next: EssentialAction): Any = {
    val essentialAction = (requestHeader: RequestHeader) ⇒ {

      def onResult(result: Result): Result = {
        Tracer.currentContext.collect { ctx ⇒
          ctx.finish()

          recordHttpServerMetrics(result.header, ctx.name)

          if (PlayExtension.includeTraceToken) result.withHeaders(PlayExtension.traceTokenHeaderName -> ctx.token)
          else result

        } getOrElse result
      }
      //override the current trace name
      Tracer.currentContext.rename(PlayExtension.generateTraceName(requestHeader))
      // Invoke the action
      next(requestHeader).map(onResult)(SameThreadExecutionContext)
    }
    pjp.proceed(Array(EssentialAction(essentialAction)))
  }

  @Before("call(* play.api.GlobalSettings.onError(..)) && args(request, ex)")
  def beforeOnError(request: TraceContextAware, ex: Throwable): Unit = {
    Tracer.currentContext.collect { ctx ⇒
      recordHttpServerMetrics(InternalServerError.header, ctx.name)
    }
  }

  def recordHttpServerMetrics(header: ResponseHeader, traceName: String): Unit =
    PlayExtension.httpServerMetrics.recordResponse(traceName, header.status.toString)
}
