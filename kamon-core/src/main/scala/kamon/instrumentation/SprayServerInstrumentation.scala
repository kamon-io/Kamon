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
    val traceContext: Option[TraceContext] = Tracer.context()
  }
}

@Aspect
class SprayServerInstrumentation {

  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(enclosing, request, closeAfterResponseCompletion, timestamp)")
  def openRequestInit(openRequest: OpenRequest, enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {}

  @After("openRequestInit(openRequest, enclosing, request, closeAfterResponseCompletion, timestamp)")
  def afterInit(openRequest: OpenRequest, enclosing: OpenRequestComponent, request: HttpRequest, closeAfterResponseCompletion: Boolean, timestamp: Long): Unit = {
  //@After("openRequestInit()")
  //def afterInit(): Unit = {
    Tracer.start
    //openRequest.traceContext
    //println("Created the context: " + Tracer.context() + " for the transaction: " + request)
    Tracer.context().map(_.entries ! Rename(request.uri.path.toString()))
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..))")
  def openRequestCreation(): Unit = {}

  @After("openRequestCreation()")
  def afterFinishingRequest(): Unit = {
//    println("Finishing a request: " + Tracer.context())

    Tracer.context().map(_.entries ! Finish())

    if(Tracer.context().isEmpty) {
      println("WOOOOOPAAAAAAAAA")
    }
  }

  @Pointcut("execution(spray.can.client.HttpHostConnector.RequestContext.new(..)) && this(ctx)")
  def requestRecordInit(ctx: TracingAwareContext): Unit = {}

  @After("requestRecordInit(ctx)")
  def whenCreatedRequestRecord(ctx: TracingAwareContext): Unit = {
    // Necessary to force the initialization of TracingAwareRequestContext at the moment of creation.
    ctx.traceContext
  }

  @Pointcut("execution(* spray.can.client.HttpHostConnectionSlot.dispatchToCommander(..)) && args(ctx, msg)")
  def requestRecordInit2(ctx: TracingAwareContext, msg: Any): Unit = {}

  @After("requestRecordInit2(ctx, msg)")
  def whenCreatedRequestRecord2(ctx: TracingAwareContext, msg: Any): Unit = {
    //println("=======> Spent in WEB External: " + (System.nanoTime() - ctx.timestamp))

    // TODO: REMOVE THIS:
//    val request = (ctx.asInstanceOf[RequestContext]).request

//    ctx.context.map(_.entries ! WebExternal(ctx.timestamp, System.nanoTime(), request.header[Host].map(_.host).getOrElse("UNKNOWN")))

  }
}

case class DefaultTracingAwareRequestContext(traceContext: Option[TraceContext] = Tracer.context(), timestamp: Long = System.nanoTime) extends TracingAwareContext

@Aspect
class SprayRequestContextTracing {

  @DeclareMixin("spray.can.client.HttpHostConnector.RequestContext")
  def mixin: TracingAwareContext = DefaultTracingAwareRequestContext()
}