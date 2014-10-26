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

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.{ HttpHeader, HttpResponse, HttpMessageEnd, HttpRequest }
import spray.http.HttpHeaders.RawHeader
import kamon.trace._
import kamon.Kamon
import kamon.spray.{ ClientSegmentCollectionStrategy, Spray }
import akka.actor.ActorRef
import scala.concurrent.{ Future, ExecutionContext }
import akka.util.Timeout

@Aspect
class ClientRequestInstrumentation {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: SegmentAware = SegmentAware.default

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(requestContext) && args(request, *, *, *)")
  def requestContextCreation(requestContext: SegmentAware, request: HttpRequest): Unit = {}

  @After("requestContextCreation(requestContext, request)")
  def afterRequestContextCreation(requestContext: SegmentAware, request: HttpRequest): Unit = {
    // The RequestContext will be copied when a request needs to be retried but we are only interested in creating the
    // segment the first time we create one.

    // The read to ctx.segmentCompletionHandle should take care of initializing the aspect timely.
    if (requestContext.segment.isEmpty) {
      TraceRecorder.currentContext match {
        case ctx: DefaultTraceContext ⇒
          val sprayExtension = Kamon(Spray)(ctx.system)

          if (sprayExtension.clientSegmentCollectionStrategy == ClientSegmentCollectionStrategy.Internal) {
            val clientRequestName = sprayExtension.assignHttpClientRequestName(request)
            val segment = ctx.startSegment(clientRequestName, SegmentMetricIdentityLabel.HttpClient)

            requestContext.segment = segment
          }

        case EmptyTraceContext ⇒ // Nothing to do here.
      }
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: SegmentAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: SegmentAware): Any = {
    TraceRecorder.withInlineTraceContextReplacement(old.traceContext) {
      pjp.proceed()
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: SegmentAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: SegmentAware, message: Any) = {
    if (requestContext.traceContext.nonEmpty) {
      TraceRecorder.withInlineTraceContextReplacement(requestContext.traceContext) {
        if (message.isInstanceOf[HttpMessageEnd])
          requestContext.segment.finish()

        pjp.proceed()
      }

    } else pjp.proceed()
  }

  @Pointcut("execution(* spray.client.pipelining$.sendReceive(akka.actor.ActorRef, *, *)) && args(transport, ec, timeout)")
  def requestLevelApiSendReceive(transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Unit = {}

  @Around("requestLevelApiSendReceive(transport, ec, timeout)")
  def aroundRequestLevelApiSendReceive(pjp: ProceedingJoinPoint, transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Any = {
    val originalSendReceive = pjp.proceed().asInstanceOf[HttpRequest ⇒ Future[HttpResponse]]

    (request: HttpRequest) ⇒ {
      val responseFuture = originalSendReceive.apply(request)

      TraceRecorder.currentContext match {
        case ctx: DefaultTraceContext ⇒
          val sprayExtension = Kamon(Spray)(ctx.system)

          if (sprayExtension.clientSegmentCollectionStrategy == ClientSegmentCollectionStrategy.Pipelining) {
            val clientRequestName = sprayExtension.assignHttpClientRequestName(request)
            val segment = ctx.startSegment(clientRequestName, SegmentMetricIdentityLabel.HttpClient)

            responseFuture.onComplete { result ⇒
              segment.finish()
            }(ec)
          }

        case EmptyTraceContext ⇒ // Nothing to do here.
      }

      responseFuture
    }

  }

  @Pointcut("call(* spray.http.HttpMessage.withDefaultHeaders(*)) && within(spray.can.client.HttpHostConnector) && args(defaultHeaders)")
  def includingDefaultHeadersAtHttpHostConnector(defaultHeaders: List[HttpHeader]): Unit = {}

  @Around("includingDefaultHeadersAtHttpHostConnector(defaultHeaders)")
  def aroundIncludingDefaultHeadersAtHttpHostConnector(pjp: ProceedingJoinPoint, defaultHeaders: List[HttpHeader]): Any = {
    val modifiedHeaders = TraceRecorder.currentContext match {
      case ctx: DefaultTraceContext ⇒
        val sprayExtension = Kamon(Spray)(ctx.system)

        if (sprayExtension.includeTraceToken)
          RawHeader(sprayExtension.traceTokenHeaderName, ctx.token) :: defaultHeaders
        else
          defaultHeaders

      case EmptyTraceContext ⇒ defaultHeaders
    }

    pjp.proceed(Array(modifiedHeaders))
  }
}
