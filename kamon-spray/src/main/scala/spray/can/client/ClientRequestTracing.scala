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
import spray.http.HttpRequest
import spray.http.HttpHeaders.Host
import kamon.trace._
import spray.http.HttpRequest
import kamon.trace.TraceContext
import kamon.trace.Segments.{HttpClientRequest, Start}
import kamon.trace.Trace.SegmentCompletionHandle

trait SegmentCompletionHandleAware {
  var completionHandle: Option[SegmentCompletionHandle]
}

trait ContextAndSegmentCompletionAware extends ContextAware with SegmentCompletionHandleAware

@Aspect
class SprayOpenRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixinContextAwareToRequestContext: ContextAndSegmentCompletionAware = new ContextAndSegmentCompletionAware {
    def traceContext: Option[TraceContext] = Trace.context()
    var completionHandle: Option[SegmentCompletionHandle] = None
  }
}

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestRecordInit(ctx: ContextAndSegmentCompletionAware, request: HttpRequest): Unit = {}

  @After("requestRecordInit(ctx, request)")
  def whenCreatedRequestRecord(ctx: ContextAndSegmentCompletionAware, request: HttpRequest): Unit = {
    val completionHandle = Trace.startSegment(Segments.Start(HttpClientRequest))

    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    ctx.traceContext
    ctx.completionHandle = Some(completionHandle)
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: TimedContextAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: ContextAndSegmentCompletionAware, message: Any) = {
    println("Completing the request with context: " + requestContext.traceContext)

    requestContext.completionHandle.map(_.complete(Segments.End()))
    /*Tracer.context.withValue(requestContext.traceContext) {
      requestContext.traceContext.map {
        tctx => //tctx.tracer ! WebExternalFinish(requestContext.timestamp)
      }
      pjp.proceed()
    }*/

  }

  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: TimedContextAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: TimedContextAware) = {
    println("Instrumenting the request context copy.")
    /*Tracer.traceContext.withValue(old.traceContext) {
      pjp.proceed()
    }*/
  }
}

@Aspect
class SprayRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: ContextAware = new ContextAware {
    val traceContext: Option[TraceContext] = Trace.context()
  }
}