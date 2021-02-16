package kamon.instrumentation.spring.server

import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import javax.servlet.http.HttpServletRequest

class SpringMVCInstrumentation extends InstrumentationBuilder {

  /*
   * Adds serverInstrumentation to HandlerAdapter, and measures execution time
   * of calling the handle method. Stores the extracted context while it's executing.
   */

  onSubTypesOf("org.springframework.web.servlet.HandlerAdapter")
    .mixin(classOf[HasServerInstrumentation.Mixin])
    .advise(method("handle"), ControllerAdvice)
}


object ControllerAdvice {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This handlerAdapter: HasServerInstrumentation,
            @Advice.Argument(0) request: HttpServletRequest): (Span, Scope) = {

    val serverInstrumentation = handlerAdapter.getServerInstrumentation(request)
    val requestHandler = serverInstrumentation.createHandler(RequestConverter.toRequest(request))
      .requestReceived()

    val scope = Kamon.storeContext(requestHandler.context)

    // should I be tupling this like this?
    (requestHandler.span, scope)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def exit(@Advice.Enter enter: (Span, Scope),
           @Advice.Thrown throwable: Throwable): Unit = {
    val (span, scope) = enter
    if (throwable != null) {
      span.fail(throwable)
    }

    span.finish()
    scope.close()
  }
}

