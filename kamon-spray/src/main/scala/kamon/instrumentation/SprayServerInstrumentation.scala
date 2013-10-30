package kamon.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import spray.http.HttpRequest
import spray.http.HttpHeaders.Host

//import spray.can.client.HttpHostConnector.RequestContext

trait ContextAware {
  def traceContext: Option[TraceContext]
}

trait TimedContextAware {
  def timestamp: Long
  def traceContext: Option[TraceContext]
}

@Aspect
class SprayOpenRequestContextTracing {
  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: ContextAware = new ContextAware {
    val traceContext: Option[TraceContext] = Tracer.traceContext.value
  }

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixinContextAwareToRequestContext: TimedContextAware = new TimedContextAware {
    val timestamp: Long = System.nanoTime()
    val traceContext: Option[TraceContext] = Tracer.traceContext.value
  }
}

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: ContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: ContextAware, request: HttpRequest): Unit = {
    Tracer.start
    openRequest.traceContext

    Tracer.context().map(_.tracer ! Rename(request.uri.path.toString()))
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest)")
  def openRequestCreation(openRequest: ContextAware): Unit = {}

  @After("openRequestCreation(openRequest)")
  def afterFinishingRequest(openRequest: ContextAware): Unit = {
    val original = openRequest.traceContext
    Tracer.context().map(_.tracer ! Finish())

    if(Tracer.context() != original) {
      println(s"OMG DIFFERENT Original: [${original}] - Came in: [${Tracer.context}]")
    }
  }

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx) && args(request, *, *, *)")
  def requestRecordInit(ctx: TimedContextAware, request: HttpRequest): Unit = {}

  @After("requestRecordInit(ctx, request)")
  def whenCreatedRequestRecord(ctx: TimedContextAware, request: HttpRequest): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    for{
      tctx <- ctx.traceContext
      host <- request.header[Host]
    } tctx.tracer ! WebExternalStart(ctx.timestamp, host.host)
  }



  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(requestContext, message)")
  def dispatchToCommander(requestContext: TimedContextAware, message: Any): Unit = {}

  @Around("dispatchToCommander(requestContext, message)")
  def aroundDispatchToCommander(pjp: ProceedingJoinPoint, requestContext: TimedContextAware, message: Any) = {
    println("Completing the request with context: " + requestContext.traceContext)

    Tracer.traceContext.withValue(requestContext.traceContext) {
      requestContext.traceContext.map {
        tctx => tctx.tracer ! WebExternalFinish(requestContext.timestamp)
      }
      pjp.proceed()
    }

  }


  @Pointcut("execution(* spray.can.client.HttpHostConnector.RequestContext.copy(..)) && this(old)")
  def copyingRequestContext(old: TimedContextAware): Unit = {}

  @Around("copyingRequestContext(old)")
  def aroundCopyingRequestContext(pjp: ProceedingJoinPoint, old: TimedContextAware) = {
    println("Instrumenting the request context copy.")
    Tracer.traceContext.withValue(old.traceContext) {
      pjp.proceed()
    }
  }
}

case class DefaultTracingAwareRequestContext(traceContext: Option[TraceContext] = Tracer.context(), timestamp: Long = System.nanoTime) extends TracingAwareContext

@Aspect
class SprayRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: TracingAwareContext = DefaultTracingAwareRequestContext()
}