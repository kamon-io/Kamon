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

package kamon.play.instrumentation

import io.netty.handler.codec.http.{HttpRequest, HttpResponse}
import kamon.Kamon
import kamon.context.Context
import kamon.play.OperationNameFilter
import kamon.trace.Span
import kamon.util.CallingThreadExecutionContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import play.api.mvc.EssentialFilter

import scala.concurrent.Future

@Aspect
class RequestHandlerInstrumentation {

  private lazy val filter: EssentialFilter = new OperationNameFilter()

  @Around("execution(* play.core.server.netty.PlayRequestHandler.handle(..)) && args(*, request)")
  def onHandle(pjp: ProceedingJoinPoint, request: HttpRequest): Any = {
    val incomingContext = decodeContext(request)
    val serverSpan = Kamon.buildSpan("unknown-operation")
      .asChildOf(incomingContext.get(Span.ContextKey))
      .withMetricTag("span.kind", "server")
      .withMetricTag("component", "play.server.netty")
      .withMetricTag("http.method", request.getMethod.name())
      .withTag("http.url", request.getUri)
      .start()

    val responseFuture = Kamon.withContext(incomingContext.withKey(Span.ContextKey, serverSpan)) {
      pjp.proceed().asInstanceOf[Future[HttpResponse]]
    }

    responseFuture.transform(
      s = response => {
        val responseStatus = response.getStatus
        serverSpan.tag("http.status_code", responseStatus.code())

        if(isError(responseStatus.code))
          serverSpan.addError(responseStatus.reasonPhrase())

        if(responseStatus.code == StatusCodes.NotFound)
          serverSpan.setOperationName("not-found")

        serverSpan.finish()
        response
      },
      f = error => {
        serverSpan.addError("error.object", error)
        serverSpan.finish()
        error
      }
    )(CallingThreadExecutionContext)
  }

  @Around("call(* play.api.http.HttpFilters.filters(..))")
  def filters(pjp: ProceedingJoinPoint): Any = {
    filter +: pjp.proceed().asInstanceOf[Seq[EssentialFilter]]
  }
}
