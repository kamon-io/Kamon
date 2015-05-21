/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package spray.can.client

import kamon.Kamon
import kamon.util.SameThreadExecutionContext
import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import spray.http._
import spray.http.HttpHeaders.RawHeader
import kamon.trace._
import kamon.spray.{ ClientInstrumentationLevel, Spray }
import akka.actor.{ ActorRef, Status }
import scala.concurrent.{ Future, ExecutionContext }
import akka.util.Timeout

@Aspect
class ClientRequestInstrumentation {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixinTraceContextAwareToRequestContext: TraceContextAware = TraceContextAware.default

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixinSegmentAwareToRequestContext: SegmentAware = SegmentAware.default

  @DeclareMixin("spray.http.HttpRequest")
  def mixinSegmentAwareToHttpRequest: SegmentAware = SegmentAware.default

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(requestContext) && args(request, *, *, *)")
  def requestContextCreation(requestContext: SegmentAware with TraceContextAware, request: HttpRequest): Unit = {}

  @After("requestContextCreation(requestContext, request)")
  def afterRequestContextCreation(requestContext: SegmentAware with TraceContextAware, request: HttpRequest): Unit = {
    // This read to requestContext.traceContext takes care of initializing the aspect timely.
    requestContext.traceContext

    Tracer.currentContext.collect { ctx ⇒
      val sprayExtension = Kamon.extension(Spray)

      if (sprayExtension.settings.clientInstrumentationLevel == ClientInstrumentationLevel.HostLevelAPI) {
        if (requestContext.segment.isEmpty) {
          val clientRequestName = sprayExtension.generateHostLevelApiSegmentName(request)
          val segment = ctx.startSegment(clientRequestName, SegmentCategory.HttpClient, Spray.SegmentLibraryName)
          requestContext.segment = segment
        }

      } else {

        // We have a Request Level API, let's just make sure that we rename it accordingly. The reason for assigning a
        // name again here is that when the request was initially sent it might not have the Host information available
        // and it might be important to decide a proper segment name.

        val clientRequestName = sprayExtension.generateHostLevelApiSegmentName(request)
        request.asInstanceOf[SegmentAware].segment.rename(clientRequestName)
      }
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: TraceContextAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: TraceContextAware): Any = {
    Tracer.withContext(old.traceContext) {
      pjp.proceed()
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: TraceContextAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: TraceContextAware, message: Any): Any = {
    if (requestContext.traceContext.nonEmpty) {
      Tracer.withContext(requestContext.traceContext) {
        if (message.isInstanceOf[HttpMessageEnd] || message.isInstanceOf[Status.Failure])
          requestContext.asInstanceOf[SegmentAware].segment.finish()

        pjp.proceed()
      }
    } else pjp.proceed()
  }

  @Pointcut("execution(* spray.http.HttpRequest.copy(..)) && this(old)")
  def copyingHttpRequest(old: SegmentAware): Unit = {}

  @Around("copyingHttpRequest(old)")
  def aroundCopyingHttpRequest(pjp: ProceedingJoinPoint, old: SegmentAware): Any = {
    val copiedHttpRequest = pjp.proceed().asInstanceOf[SegmentAware]
    copiedHttpRequest.segment = old.segment
    copiedHttpRequest
  }

  @Pointcut("execution(* spray.client.pipelining$.sendReceive(akka.actor.ActorRef, *, *)) && args(transport, ec, timeout)")
  def requestLevelApiSendReceive(transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Unit = {}

  @Around("requestLevelApiSendReceive(transport, ec, timeout)")
  def aroundRequestLevelApiSendReceive(pjp: ProceedingJoinPoint, transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Any = {
    val originalSendReceive = pjp.proceed().asInstanceOf[HttpRequest ⇒ Future[HttpResponse]]

    (request: HttpRequest) ⇒ {
      Tracer.currentContext.collect { ctx ⇒
        val sprayExtension = Kamon.extension(Spray)
        val segment =
          if (sprayExtension.settings.clientInstrumentationLevel == ClientInstrumentationLevel.RequestLevelAPI)
            ctx.startSegment(sprayExtension.generateRequestLevelApiSegmentName(request), SegmentCategory.HttpClient, Spray.SegmentLibraryName)
          else
            EmptyTraceContext.EmptySegment

        request.asInstanceOf[SegmentAware].segment = segment

        val responseFuture = originalSendReceive.apply(request)
        responseFuture.onComplete(_ ⇒ segment.finish())(SameThreadExecutionContext)
        responseFuture

      } getOrElse originalSendReceive.apply(request)
    }
  }

  @Pointcut("execution(* spray.http.HttpMessage.withDefaultHeaders(*)) && this(request) && args(defaultHeaders)")
  def includingDefaultHeadersAtHttpHostConnector(request: HttpMessage, defaultHeaders: List[HttpHeader]): Unit = {}

  @Around("includingDefaultHeadersAtHttpHostConnector(request, defaultHeaders)")
  def aroundIncludingDefaultHeadersAtHttpHostConnector(pjp: ProceedingJoinPoint, request: HttpMessage, defaultHeaders: List[HttpHeader]): Any = {

    val modifiedHeaders = Tracer.currentContext.collect { ctx ⇒
      val sprayExtension = Kamon.extension(Spray)
      if (sprayExtension.settings.includeTraceTokenHeader)
        RawHeader(sprayExtension.settings.traceTokenHeaderName, ctx.token) :: defaultHeaders
      else
        defaultHeaders

    } getOrElse (defaultHeaders)

    pjp.proceed(Array[AnyRef](request, modifiedHeaders))
  }
}
