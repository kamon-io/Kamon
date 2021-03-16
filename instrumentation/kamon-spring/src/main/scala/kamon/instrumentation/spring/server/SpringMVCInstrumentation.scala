package kamon.instrumentation.spring.server

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
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
    .advise(method("processHandlerException"), ProcessHandlerExceptionAdvice)
    .advise(method("getHandler").and(takesArguments(1)), GetHandlerAdvice)

  /*
   * Changes Callable argument of startCallableProcessing with an
   * instrumented one that stores the context when called.
   */
  onType("org.springframework.web.context.request.async.WebAsyncManager")
    .advise(
      method("startCallableProcessing")
        .and(withArgument(0, classOf[Callable[_]])), classOf[CallableWrapper])
}

object DispatchAdvice {
  @Advice.OnMethodEnter()
  def enter(@Advice.This dispatcherServlet: HasServerInstrumentation,
            @Advice.Argument(0) request: HttpServletRequest): (RequestHandler, Scope) = {
    val requestHandler = Option(request.getAttribute("kamon-handler").asInstanceOf[RequestHandler])
      .getOrElse({
        val serverInstrumentation = dispatcherServlet.getServerInstrumentation(request)
        val handler = serverInstrumentation
          .createHandler(InstrumentationUtils.requestReader(request), true)
        request.setAttribute("kamon-handler", handler)
        handler
      })
    val scope = Kamon.storeContext(requestHandler.context)

    (requestHandler, scope)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter enter: (RequestHandler, Scope),
           @Advice.Argument(0) request: HttpServletRequest,
           @Advice.Argument(1) response: HttpServletResponse): Unit = {
    val (handler, scope) = enter

    if (response.isCommitted) {
      handler.buildResponse(InstrumentationUtils.responseBuilder(response), handler.context)
      handler.responseSent()
      request.removeAttribute("kamon-handler")
    }

    scope.close()
  }
}

object GetHandlerAdvice {
  @Advice.OnMethodExit()
  def exit(@Advice.Argument(0) request: HttpServletRequest): Unit = {
    val handler = request.getAttribute("kamon-handler").asInstanceOf[RequestHandler]
    val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)

    if (handler != null && pattern != null) {
      handler.span.name(pattern.toString)
      handler.span.takeSamplingDecision()
    }
  }
}

object ProcessHandlerExceptionAdvice {
  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Argument(3) throwable: Throwable): Unit = {
    if (throwable != null) {
      Kamon.currentSpan().fail(throwable)
    }
  }
}

object RenderAdvice {
  @Advice.OnMethodEnter()
  def enter(): Span =
    Kamon.internalSpanBuilder("view.render", "spring.server").start()

  @Advice.OnMethodExit()
  def exit(@Advice.Enter span: Span): Unit =
    span.finish()
}
