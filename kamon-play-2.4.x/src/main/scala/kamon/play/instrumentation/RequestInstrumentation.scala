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
import kamon.Kamon.tracer
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
  def mixinContextAwareToRequestHeader: TraceContextAware = TraceContextAware.default

  @Before("call(* play.api.http.DefaultHttpRequestHandler.routeRequest(..)) && args(requestHeader)")
  def routeRequest(requestHeader: RequestHeader): Unit = {
    val token = if (PlayExtension.includeTraceToken) {
      requestHeader.headers.get(PlayExtension.traceTokenHeaderName)
    } else None

    Tracer.setCurrentContext(tracer.newContext("UnnamedTrace", token))
  }

  @Around("call(* play.api.http.HttpFilters.filters(..))")
  def filters(pjp: ProceedingJoinPoint): Any = {
    val filter = new EssentialFilter {
      def apply(next: EssentialAction) = EssentialAction((requestHeader) ⇒ {
        def onResult(result: Result): Result = {
          Tracer.currentContext.collect { ctx ⇒
            ctx.finish()
            PlayExtension.httpServerMetrics.recordResponse(ctx.name, result.header.status.toString)

            if (PlayExtension.includeTraceToken) result.withHeaders(PlayExtension.traceTokenHeaderName -> ctx.token)
            else result

          } getOrElse result
        }
        //override the current trace name
        Tracer.currentContext.rename(PlayExtension.generateTraceName(requestHeader))
        // Invoke the action
        next(requestHeader).map(onResult)(SameThreadExecutionContext)
      })
    }
    pjp.proceed().asInstanceOf[Seq[EssentialFilter]] :+ filter
  }

  @Before("call(* play.api.http.HttpErrorHandler.onClientServerError(..)) && args(requestContextAware, statusCode, *)")
  def onClientError(requestContextAware: TraceContextAware, statusCode: Int): Unit = {
    requestContextAware.traceContext.collect { ctx ⇒
      PlayExtension.httpServerMetrics.recordResponse(ctx.name, statusCode.toString)
    }
  }

  @Before("call(* play.api.http.HttpErrorHandler.onServerError(..)) && args(requestContextAware, ex)")
  def onServerError(requestContextAware: TraceContextAware, ex: Throwable): Unit = {
    requestContextAware.traceContext.collect { ctx ⇒
      PlayExtension.httpServerMetrics.recordResponse(ctx.name, InternalServerError.header.status.toString)
    }
  }
}
