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
import spray.http.HttpHeaders.{ RawHeader, Host }
import kamon.trace.{SegmentAware, TraceRecorder, SegmentCompletionHandleAware}
import kamon.metric.TraceMetrics.HttpClientRequest
import kamon.Kamon
import kamon.spray.{ ClientSegmentCollectionStrategy, Spray }
import akka.actor.ActorRef
import scala.concurrent.{ Future, ExecutionContext }
import akka.util.Timeout

@Aspect
class ClientRequestInstrumentation {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: SegmentAware = SegmentAware.default

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestContextCreation(ctx: SegmentAware, request: HttpRequest): Unit = {}

  @After("requestContextCreation(ctx, request)")
  def afterRequestContextCreation(ctx: SegmentAware, request: HttpRequest): Unit = {
    // The RequestContext will be copied when a request needs to be retried but we are only interested in creating the
    // completion handle the first time we create one.

    // The read to ctx.segmentCompletionHandle should take care of initializing the aspect timely.
    if (ctx.segmentCompletionHandle.isEmpty) {
      TraceRecorder.currentContext.map { traceContext ⇒
        val sprayExtension = Kamon(Spray)(traceContext.system)

        if (sprayExtension.clientSegmentCollectionStrategy == ClientSegmentCollectionStrategy.Internal) {
          val requestAttributes = basicRequestAttributes(request)
          val clientRequestName = sprayExtension.assignHttpClientRequestName(request)
          val completionHandle = traceContext.startSegment(HttpClientRequest(clientRequestName), requestAttributes)

          ctx.segmentCompletionHandle = Some(completionHandle)
        }
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
    requestContext.traceContext match {
      case ctx @ Some(_) ⇒
        TraceRecorder.withInlineTraceContextReplacement(ctx) {
          if (message.isInstanceOf[HttpMessageEnd])
            requestContext.segment.finish()

          pjp.proceed()
        }

      case None ⇒ pjp.proceed()
    }
  }

  @Pointcut("execution(* spray.client.pipelining$.sendReceive(akka.actor.ActorRef, *, *)) && args(transport, ec, timeout)")
  def requestLevelApiSendReceive(transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Unit = {}

  @Around("requestLevelApiSendReceive(transport, ec, timeout)")
  def aroundRequestLevelApiSendReceive(pjp: ProceedingJoinPoint, transport: ActorRef, ec: ExecutionContext, timeout: Timeout): Any = {
    val originalSendReceive = pjp.proceed().asInstanceOf[HttpRequest ⇒ Future[HttpResponse]]

    (request: HttpRequest) ⇒ {
      val responseFuture = originalSendReceive.apply(request)
      TraceRecorder.currentContext.map { traceContext ⇒
        val sprayExtension = Kamon(Spray)(traceContext.system)

        if (sprayExtension.clientSegmentCollectionStrategy == ClientSegmentCollectionStrategy.Pipelining) {
          val requestAttributes = basicRequestAttributes(request)
          val clientRequestName = sprayExtension.assignHttpClientRequestName(request)
          val completionHandle = traceContext.startSegment(HttpClientRequest(clientRequestName), requestAttributes)

          responseFuture.onComplete { result ⇒
            completionHandle.finish(Map.empty)
          }(ec)
        }
      }

      responseFuture
    }

  }

  def basicRequestAttributes(request: HttpRequest): Map[String, String] = {
    Map[String, String](
      "host" -> request.header[Host].map(_.value).getOrElse("unknown"),
      "path" -> request.uri.path.toString(),
      "method" -> request.method.toString())
  }

  @Pointcut("call(* spray.http.HttpMessage.withDefaultHeaders(*)) && within(spray.can.client.HttpHostConnector) && args(defaultHeaders)")
  def includingDefaultHeadersAtHttpHostConnector(defaultHeaders: List[HttpHeader]): Unit = {}

  @Around("includingDefaultHeadersAtHttpHostConnector(defaultHeaders)")
  def aroundIncludingDefaultHeadersAtHttpHostConnector(pjp: ProceedingJoinPoint, defaultHeaders: List[HttpHeader]): Any = {
    val modifiedHeaders = TraceRecorder.currentContext map { traceContext ⇒
      val sprayExtension = Kamon(Spray)(traceContext.system)

      if (sprayExtension.includeTraceToken)
        RawHeader(sprayExtension.traceTokenHeaderName, traceContext.token) :: defaultHeaders
      else
        defaultHeaders
    } getOrElse defaultHeaders

    pjp.proceed(Array(modifiedHeaders))
  }
}
