package kamon.instrumentation.spring.server

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class SpringMVCInstrumentation extends InstrumentationBuilder {

  /*
   * Adds serverInstrumentation to HandlerAdapter, and measures execution time
   * of calling the handle method. Stores the extracted context while it's executing.
   */
  //  onType("org.springframework.web.servlet.DispatcherServlet")
  //    .mixin(classOf[HasServerInstrumentation.Mixin])
  //    .advise(method("doDispatch"), ControllerAdvice)

  onSubTypesOf("org.springframework.web.servlet.HandlerAdapter")
    .mixin(classOf[HasServerInstrumentation.Mixin])
    .advise(method("handle"), ControllerAdvice)
}


object ControllerAdvice {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This handlerAdapter: HasServerInstrumentation,
            @Advice.Argument(0) request: HttpServletRequest): (RequestHandler, Scope) = {
    val serverInstrumentation = handlerAdapter
      .getServerInstrumentation(request)

    val requestHandler = serverInstrumentation
      .createHandler(InstrumentationUtils.requestReader(request))
      .requestReceived()

    Option(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
      .map(_.toString)
      // Extract name from path if it matches a pattern
      // otherwise, use `http.server.request`
      .foreach(requestHandler.span.name)

    val scope = Kamon.storeContext(requestHandler.context)

    // should I be tupling this like this?
    (requestHandler, scope)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter enter: (RequestHandler, Scope),
           @Advice.Argument(1) response: HttpServletResponse,
           @Advice.Thrown throwable: Throwable): Unit = {
    val (handler, scope) = enter

    if (throwable != null) {
      handler.span.fail(throwable)
    }

    handler.buildResponse(InstrumentationUtils.responseBuilder(response), handler.context)
    handler.responseSent()
    scope.close()
  }
}

