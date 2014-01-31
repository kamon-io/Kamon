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
package spray.can.server

import org.aspectj.lang.annotation._
import kamon.trace.{ TraceContext, TraceRecorder, TraceContextAware }
import akka.actor.ActorSystem
import spray.http.{ HttpResponse, HttpMessagePartWrapper, HttpRequest }
import akka.event.Logging.Warning
import scala.Some
import kamon.Kamon
import kamon.spray.Spray
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
    val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
    val sprayExtension = Kamon(Spray)(system)

    val defaultTraceName: String = request.method.value + ": " + request.uri.path
    val token = if (sprayExtension.includeTraceToken) {
      request.headers.find(_.name == sprayExtension.traceTokenHeaderName).map(_.value)
    } else None

    TraceRecorder.start(defaultTraceName, token)(system)

    // Necessary to force initialization of traceContext when initiating the request.
    openRequest.traceContext
  }

  @Pointcut("execution(* spray.can.server.ServerFrontend$$anon$2$$anon$1.spray$can$server$ServerFrontend$$anon$$anon$$openNewRequest(..))")
  def openNewRequest(): Unit = {}

  @After("openNewRequest()")
  def afterOpenNewRequest(): Unit = {
    TraceRecorder.clearContext
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest) && args(response)")
  def openRequestCreation(openRequest: TraceContextAware, response: HttpMessagePartWrapper): Unit = {}

  @Around("openRequestCreation(openRequest, response)")
  def afterFinishingRequest(pjp: ProceedingJoinPoint, openRequest: TraceContextAware, response: HttpMessagePartWrapper): Any = {
    val incomingContext = TraceRecorder.currentContext
    val storedContext = openRequest.traceContext

    verifyTraceContextConsistency(incomingContext, storedContext)
    val proceedResult = incomingContext match {
      case None ⇒ pjp.proceed()
      case Some(traceContext) ⇒
        val sprayExtension = Kamon(Spray)(traceContext.system)

        if (sprayExtension.includeTraceToken) {
          val responseWithHeader = includeTraceTokenIfPossible(response, sprayExtension.traceTokenHeaderName, traceContext.token)
          pjp.proceed(Array(openRequest, responseWithHeader))

        } else pjp.proceed
    }

    TraceRecorder.finish()
    proceedResult
  }

  def verifyTraceContextConsistency(incomingTraceContext: Option[TraceContext], storedTraceContext: Option[TraceContext]): Unit = {
    for (original ← storedTraceContext) {
      incomingTraceContext match {
        case Some(incoming) if original.token != incoming.token ⇒
          publishWarning(s"Different ids when trying to close a Trace, original: [$original] - incoming: [$incoming]", incoming.system)

        case Some(_) ⇒ // nothing to do here.

        case None ⇒
          publishWarning(s"Trace context not present while closing the Trace: [$original]", original.system)
      }
    }

    def publishWarning(text: String, system: ActorSystem): Unit =
      system.eventStream.publish(Warning("", classOf[ServerRequestInstrumentation], text))

  }

  def includeTraceTokenIfPossible(response: HttpMessagePartWrapper, traceTokenHeaderName: String, token: String): HttpMessagePartWrapper =
    response match {
      case response: HttpResponse ⇒ response.withHeaders(RawHeader(traceTokenHeaderName, token))
      case other                  ⇒ other
    }
}
