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

package spray.can.client

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.{ HttpMessageEnd, HttpRequest }
import spray.http.HttpHeaders.Host
import kamon.trace.{ TraceRecorder, SegmentCompletionHandleAware, TraceContextAware }
import kamon.metrics.TraceMetrics.HttpClientRequest
import kamon.Kamon
import kamon.spray.Spray

@Aspect
class ClientRequestTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: SegmentCompletionHandleAware = SegmentCompletionHandleAware.default

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestContextCreation(ctx: SegmentCompletionHandleAware, request: HttpRequest): Unit = {}

  @After("requestContextCreation(ctx, request)")
  def afterRequestContextCreation(ctx: SegmentCompletionHandleAware, request: HttpRequest): Unit = {
    // The RequestContext will be copied when a request needs to be retried but we are only interested in creating the
    // completion handle the first time we create one.

    // The read to ctx.completionHandle should take care of initializing the aspect timely.
    if (ctx.segmentCompletionHandle.isEmpty) {
      TraceRecorder.currentContext.map { traceContext ⇒
        val requestAttributes = Map[String, String](
          "host" -> request.header[Host].map(_.value).getOrElse("unknown"),
          "path" -> request.uri.path.toString(),
          "method" -> request.method.toString())

        val clientRequestName = Kamon(Spray)(traceContext.system).assignHttpClientRequestName(request)
        val completionHandle = traceContext.startSegment(HttpClientRequest(clientRequestName), requestAttributes)
        ctx.segmentCompletionHandle = Some(completionHandle)
      }
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: SegmentCompletionHandleAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: SegmentCompletionHandleAware): Any = {
    TraceRecorder.withTraceContext(old.traceContext) {
      pjp.proceed()
    }
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: SegmentCompletionHandleAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: SegmentCompletionHandleAware, message: Any) = {
    requestContext.traceContext match {
      case ctx @ Some(_) ⇒
        TraceRecorder.withTraceContext(ctx) {
          if (message.isInstanceOf[HttpMessageEnd])
            requestContext.segmentCompletionHandle.map(_.finish(Map.empty))

          pjp.proceed()
        }

      case None ⇒ pjp.proceed()
    }
  }

}
