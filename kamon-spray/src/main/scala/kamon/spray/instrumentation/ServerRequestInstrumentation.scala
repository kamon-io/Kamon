/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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
package spray.can.server.instrumentation

import org.aspectj.lang.annotation._
import kamon.trace._
import spray.http.{ HttpResponse, HttpMessagePartWrapper, HttpRequest }
import kamon.Kamon
import kamon.spray.SprayExtension
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.HttpHeaders.RawHeader

@Aspect
class ServerRequestInstrumentation {

  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {
    import Kamon.tracer

    val defaultTraceName = SprayExtension.generateTraceName(request)
    val token = if (SprayExtension.settings.includeTraceTokenHeader) {
      request.headers.find(_.name == SprayExtension.settings.traceTokenHeaderName).map(_.value)
    } else None

    val newContext = tracer.newContext(defaultTraceName, token)
    Tracer.setCurrentContext(newContext)

    // Necessary to force initialization of traceContext when initiating the request.
    openRequest.traceContext
  }

  @Pointcut("execution(* spray.can.server.ServerFrontend$$anon$2$$anon$1.spray$can$server$ServerFrontend$$anon$$anon$$openNewRequest(..))")
  def openNewRequest(): Unit = {}

  @After("openNewRequest()")
  def afterOpenNewRequest(): Unit = {
    Tracer.clearCurrentContext
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest) && args(response)")
  def openRequestCreation(openRequest: TraceContextAware, response: HttpMessagePartWrapper): Unit = {}

  @Around("openRequestCreation(openRequest, response)")
  def afterFinishingRequest(pjp: ProceedingJoinPoint, openRequest: TraceContextAware, response: HttpMessagePartWrapper): Any = {
    val incomingContext = Tracer.currentContext
    val storedContext = openRequest.traceContext

    // The stored context is always a DefaultTraceContext if the instrumentation is running
    verifyTraceContextConsistency(incomingContext, storedContext)

    if (incomingContext.isEmpty)
      pjp.proceed()
    else {
      val proceedResult = if (SprayExtension.settings.includeTraceTokenHeader) {
        val responseWithHeader = includeTraceTokenIfPossible(response, SprayExtension.settings.traceTokenHeaderName, incomingContext.token)
        pjp.proceed(Array(openRequest, responseWithHeader))

      } else pjp.proceed

      Tracer.currentContext.finish()

      recordHttpServerMetrics(response, incomingContext.name)

      proceedResult
    }
  }

  def verifyTraceContextConsistency(incomingTraceContext: TraceContext, storedTraceContext: TraceContext): Unit = {
    def publishWarning(text: String): Unit =
      SprayExtension.log.warn(text)

    if (incomingTraceContext.nonEmpty) {
      if (incomingTraceContext.token != storedTraceContext.token)
        publishWarning(s"Different trace token found when trying to close a trace, original: [${storedTraceContext.token}] - incoming: [${incomingTraceContext.token}]")
    } else
      publishWarning(s"EmptyTraceContext present while closing the trace with token [${storedTraceContext.token}]")
  }

  def recordHttpServerMetrics(response: HttpMessagePartWrapper, traceName: String): Unit =
    response match {
      case httpResponse: HttpResponse ⇒ SprayExtension.httpServerMetrics.recordResponse(traceName, httpResponse.status.intValue.toString)
      case other                      ⇒ // Nothing to do then.
    }

  def includeTraceTokenIfPossible(response: HttpMessagePartWrapper, traceTokenHeaderName: String, token: String): HttpMessagePartWrapper =
    response match {
      case response: HttpResponse ⇒ response.withHeaders(response.headers ::: RawHeader(traceTokenHeaderName, token) :: Nil)
      case other                  ⇒ other
    }
}
