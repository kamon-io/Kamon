package kamon.instrumentation

import org.aspectj.lang.annotation.{DeclareMixin, After, Pointcut, Aspect}
import kamon.{TraceContext, Tracer}
import kamon.trace.UowTracing.{Finish, Rename}
import spray.http.HttpRequest
import spray.can.server.{OpenRequest, OpenRequestComponent}
import kamon.trace.context.TracingAwareContext

//import spray.can.client.HttpHostConnector.RequestContext

trait ContextAware {
  def traceContext: Option[TraceContext]
}

@Aspect
class SprayOpenRequestContextTracing {
  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: ContextAware = new ContextAware {
    val traceContext: Option[TraceContext] = Tracer.traceContext.value
  }
}

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(enclosing, request, closeAfterResponseCompletion, timestamp)")
  def openRequestInit(openRequest: OpenRequest, enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {}

  @After("openRequestInit(openRequest, enclosing, request, closeAfterResponseCompletion, timestamp)")
  def afterInit(openRequest: OpenRequest, enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {
    Tracer.start
    val discard = openRequest.asInstanceOf[ContextAware].traceContext

    Tracer.context().map(_.tracer ! Rename(request.uri.path.toString()))
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest)")
  def openRequestCreation(openRequest: OpenRequest): Unit = {}

  @After("openRequestCreation(openRequest)")
  def afterFinishingRequest(openRequest: OpenRequest): Unit = {
    val original = openRequest.asInstanceOf[ContextAware].traceContext

    Tracer.context().map(_.tracer ! Finish())

    if(Tracer.context() != original) {
      println(s"OMG DIFFERENT Original: [${original}] - Came in: [${Tracer.context}]")
    }
  }

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx)")
  def requestRecordInit(ctx: TracingAwareContext): Unit = {}

  @After("requestRecordInit(ctx)")
  def whenCreatedRequestRecord(ctx: TracingAwareContext): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    ctx.traceContext
  }
}

case class DefaultTracingAwareRequestContext(traceContext: Option[TraceContext] = Tracer.context(), timestamp: Long = System.nanoTime) extends TracingAwareContext

@Aspect
class SprayRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: TracingAwareContext = DefaultTracingAwareRequestContext()
}