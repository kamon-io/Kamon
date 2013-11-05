package spray.can.server

import org.aspectj.lang.annotation.{After, Pointcut, DeclareMixin, Aspect}
import kamon.trace.{Trace, TraceContext, ContextAware}
import spray.http.HttpRequest
import akka.actor.ActorSystem
import akka.event.Logging.Warning


@Aspect
class ServerRequestTracing {

  @DeclareMixin("spray.can.server.OpenRequestComponent.DefaultOpenRequest")
  def mixinContextAwareToOpenRequest: ContextAware = ContextAware.default


  @Pointcut("execution(spray.can.server.OpenRequestComponent$DefaultOpenRequest.new(..)) && this(openRequest) && args(*, request, *, *)")
  def openRequestInit(openRequest: ContextAware, request: HttpRequest): Unit = {}

  @After("openRequestInit(openRequest, request)")
  def afterInit(openRequest: ContextAware, request: HttpRequest): Unit = {
    val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
    val defaultTraceName: String = request.method.value + ": " + request.uri.path

    Trace.start(defaultTraceName)(system)

    // Necessary to force initialization of traceContext when initiating the request.
    openRequest.traceContext
  }

  @Pointcut("execution(* spray.can.server.OpenRequestComponent$DefaultOpenRequest.handleResponseEndAndReturnNextOpenRequest(..)) && target(openRequest)")
  def openRequestCreation(openRequest: ContextAware): Unit = {}

  @After("openRequestCreation(openRequest)")
  def afterFinishingRequest(openRequest: ContextAware): Unit = {
    val storedContext = openRequest.traceContext
    val incomingContext = Trace.finish()

    for(original <- storedContext) {
      incomingContext match {
        case Some(incoming) if original.id != incoming.id =>
          publishWarning(s"Different ids when trying to close a Trace, original: [$original] - incoming: [$incoming]")

        case Some(_) => // nothing to do here.
          
        case None =>
          publishWarning(s"Trace context not present while closing the Trace: [$original]")
      }
    }

    def publishWarning(text: String): Unit = {
      val system: ActorSystem = openRequest.asInstanceOf[OpenRequest].context.actorContext.system
      system.eventStream.publish(Warning("", classOf[ServerRequestTracing], text))
    }
  }
}
