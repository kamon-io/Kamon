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
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.HttpRequest
import spray.http.HttpHeaders.Host
import kamon.trace.{ TraceContext, Trace, ContextAware, TimedContextAware }

//import spray.can.client.HttpHostConnector.RequestContext

@Aspect
class SprayOpenRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixinContextAwareToRequestContext: TimedContextAware = new TimedContextAware {
    val timestamp: Long = System.nanoTime()
    val traceContext: Option[TraceContext] = Trace.context()
  }
}

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestRecordInit(ctx: TimedContextAware, request: HttpRequest): Unit = {}

  @After("requestRecordInit(ctx, request)")
  def whenCreatedRequestRecord(ctx: TimedContextAware, request: HttpRequest): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    /*for{
      tctx <- ctx.traceContext
      host <- request.header[Host]
    } tctx.tracer ! WebExternalStart(ctx.timestamp, host.host)*/
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: TimedContextAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: TimedContextAware, message: Any) = {
    println("Completing the request with context: " + requestContext.traceContext)

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