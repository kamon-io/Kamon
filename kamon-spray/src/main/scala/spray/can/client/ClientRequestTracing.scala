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
import spray.http.{HttpMessageEnd, HttpRequest}
import spray.http.HttpHeaders.Host
import kamon.trace.{TraceContext, Trace, Segments}
import kamon.trace.Segments.{ContextAndSegmentCompletionAware, HttpClientRequest}
import kamon.trace.Trace.SegmentCompletionHandle

@Aspect
class ClientRequestTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: ContextAndSegmentCompletionAware = new ContextAndSegmentCompletionAware {
    val traceContext: Option[TraceContext] = Trace.context()
    var completionHandle: Option[SegmentCompletionHandle] = None
  }


  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestContextCreation(ctx: ContextAndSegmentCompletionAware, request: HttpRequest): Unit = {}

  @After("requestContextCreation(ctx, request)")
  def afterRequestContextCreation(ctx: ContextAndSegmentCompletionAware, request: HttpRequest): Unit = {
    // The RequestContext will be copied when a request needs to be retried but we are only interested in creating the
    // completion handle the first time we create one.

    // The read to ctx.completionHandle should take care of initializing the aspect timely.
    if(ctx.completionHandle.isEmpty) {
      val requestAttributes = Map[String, String](
        "host" -> request.header[Host].map(_.value).getOrElse("unknown"),
        "path" -> request.uri.path.toString(),
        "method" -> request.method.toString()
      )
      val completionHandle = Trace.startSegment(category = HttpClientRequest, attributes = requestAttributes)
      ctx.completionHandle = Some(completionHandle)
    }
  }


  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: ContextAndSegmentCompletionAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: ContextAndSegmentCompletionAware) = {
    Trace.withContext(old.traceContext) {
      pjp.proceed()
    }
  }


  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: ContextAndSegmentCompletionAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: ContextAndSegmentCompletionAware, message: Any) = {
    requestContext.traceContext match {
      case ctx @ Some(_) =>
        Trace.withContext(ctx) {
          if(message.isInstanceOf[HttpMessageEnd])
            requestContext.completionHandle.map(_.complete(Segments.End()))

          pjp.proceed()
        }

      case None => pjp.proceed()
    }
  }


}