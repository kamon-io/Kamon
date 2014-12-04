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

import kamon.trace.TraceLocal.{ HttpContext, HttpContextKey }
import org.aspectj.lang.annotation._
import kamon.trace._
import akka.actor.ActorSystem
import spray.http.{ HttpResponse, HttpMessagePartWrapper, HttpRequest }
import akka.event.Logging.Warning
import kamon.Kamon
import kamon.spray.{ SprayExtension, Spray }
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.HttpHeaders.RawHeader

@Aspect
class ServerRequestInstrumentation {

  import ServerRequestInstrumentation._

  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: TraceContextAware, request: HttpRequest): Unit = {
    val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
    val sprayExtension = Kamon(Spray)(system)

    val defaultTraceName = sprayExtension.generateTraceName(request)
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

    // The stored context is always a DefaultTraceContext if the instrumentation is running
    val system = storedContext.system

    verifyTraceContextConsistency(incomingContext, storedContext, system)

    if (incomingContext.isEmpty)
      pjp.proceed()
    else {
      val sprayExtension = Kamon(Spray)(system)

      val proceedResult = if (sprayExtension.includeTraceToken) {
        val responseWithHeader = includeTraceTokenIfPossible(response, sprayExtension.traceTokenHeaderName, incomingContext.token)
        pjp.proceed(Array(openRequest, responseWithHeader))

      } else pjp.proceed

      TraceRecorder.finish()

      recordHttpServerMetrics(response, incomingContext.name, sprayExtension)

      //store in TraceLocal useful data to diagnose errors
      storeDiagnosticData(openRequest)

      proceedResult
    }
  }

  def verifyTraceContextConsistency(incomingTraceContext: TraceContext, storedTraceContext: TraceContext, system: ActorSystem): Unit = {
    def publishWarning(text: String, system: ActorSystem): Unit =
      system.eventStream.publish(Warning("ServerRequestInstrumentation", classOf[ServerRequestInstrumentation], text))

    if (incomingTraceContext.nonEmpty) {
      if (incomingTraceContext.token != storedTraceContext.token)
        publishWarning(s"Different trace token found when trying to close a trace, original: [${storedTraceContext.token}] - incoming: [${incomingTraceContext.token}]", system)
    } else
      publishWarning(s"EmptyTraceContext present while closing the trace with token [${storedTraceContext.token}]", system)
  }

  def recordHttpServerMetrics(response: HttpMessagePartWrapper, traceName: String, sprayExtension: SprayExtension): Unit =
    response match {
      case httpResponse: HttpResponse ⇒ sprayExtension.httpServerMetrics.recordResponse(traceName, httpResponse.status.intValue.toString)
      case other                      ⇒ // Nothing to do then.
    }

  def includeTraceTokenIfPossible(response: HttpMessagePartWrapper, traceTokenHeaderName: String, token: String): HttpMessagePartWrapper =
    response match {
      case response: HttpResponse ⇒ response.withHeaders(response.headers ::: RawHeader(traceTokenHeaderName, token) :: Nil)
      case other                  ⇒ other
    }

  def storeDiagnosticData(currentContext: TraceContextAware): Unit = {
    val request = currentContext.asInstanceOf[OpenRequest].request
    val headers = request.headers.map(header ⇒ header.name -> header.value).toMap
    val agent = headers.getOrElse(UserAgent, Unknown)
    val forwarded = headers.getOrElse(XForwardedFor, Unknown)

    TraceLocal.store(HttpContextKey)(HttpContext(agent, request.uri.toRelative.toString(), forwarded))
  }
}

object ServerRequestInstrumentation {
  val UserAgent = "User-Agent"
  val XForwardedFor = "X-Forwarded-For"
  val Unknown = "unknown"
}
