package kamon.instrumentation.spring.server

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers.{named, takesArgument}
import org.springframework.web.servlet.HandlerMapping

import java.util.concurrent.Callable
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class SpringMVCInstrumentation extends InstrumentationBuilder {

  /*
   * Adds serverInstrumentation to DispatcherServlet, measures execution time
   * of calling the doDispatch method and propagates context
   */
  onType("org.springframework.web.servlet.DispatcherServlet")
    .mixin(classOf[HasServerInstrumentation.Mixin])
    .advise(method("doDispatch"), DispatchAdvice)
    .advise(method("render"), RenderAdvice)
    .advise(method("processHandlerException"), ProcessHandlerAdvice)

  /*
   * Changes Callable argument of startCallableProcesing with an
   * instrumented one that stores the context when called.
   */

  onType("org.springframework.web.context.request.async.WebAsyncManager")
    .advise(
      method("startCallableProcessing")
        // is this [_] ok?
        .and(withArgument(0, classOf[Callable[_]])), classOf[CallableWrapper])
}

object ProcessHandlerAdvice {
  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Argument(3) throwable: Throwable): Unit =
    Option(throwable).map(Kamon.currentSpan().fail)

}

object RenderAdvice {
  @Advice.OnMethodEnter()
  def enter(): Span =
    Kamon.serverSpanBuilder("view.render", "spring.server").start()

  @Advice.OnMethodExit()
  def exit(@Advice.Enter span: Span): Unit =
    span.finish()
}

object DispatchAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.This dispatcherServlet: HasServerInstrumentation,
            @Advice.Argument(0) request: HttpServletRequest,
            @Advice.Argument(1) response: HttpServletResponse): (RequestHandler, Scope) = {
    val requestHandler = Option(request.getAttribute("kamon-handler"))
      .getOrElse({
        val serverInstrumentation = dispatcherServlet.getServerInstrumentation(request)
        val handler = serverInstrumentation.createHandler(InstrumentationUtils.requestReader(request))
        request.setAttribute("kamon-handler", handler)
        handler
      }).asInstanceOf[RequestHandler] // cast because .getAttribute returns AnyRef
    val scope = Kamon.storeContext(requestHandler.context)

    // should I be tupling this like this?
    (requestHandler, scope)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter enter: (RequestHandler, Scope),
           @Advice.Argument(0) request: HttpServletRequest,
           @Advice.Argument(1) response: HttpServletResponse): Unit = {
    val (handler, scope) = enter

    // Extract name from path if it matches a pattern
    // otherwise, use `http.server.request`
    Option(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
      .map(_.toString)
      .foreach(handler.span.name)

    if (response.isCommitted) {
      handler.buildResponse(InstrumentationUtils.responseBuilder(response), handler.context)
      handler.responseSent()
      request.removeAttribute("kamon-handler")
    }

    scope.close()
  }
}
