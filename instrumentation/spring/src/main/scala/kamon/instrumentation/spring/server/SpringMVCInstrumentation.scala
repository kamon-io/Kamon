/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import scala.annotation.static

class SpringMVCInstrumentation extends InstrumentationBuilder {

  /*
   * Adds serverInstrumentation to DispatcherServlet, measures execution time
   * of calling the doDispatch method and propagates context
   */
  onType("org.springframework.web.servlet.DispatcherServlet")
    .mixin(classOf[HasServerInstrumentation.Mixin])
    .advise(method("doDispatch"), classOf[DispatchAdvice])
    .advise(method("render"), classOf[RenderAdvice])
    .advise(method("processHandlerException"), classOf[ProcessHandlerExceptionAdvice])
    .advise(method("getHandler").and(takesArguments(1)), classOf[GetHandlerAdvice])

  /*
   * Changes Callable argument of startCallableProcessing with an
   * instrumented one that stores the context when called.
   */
  onType("org.springframework.web.context.request.async.WebAsyncManager")
    .advise(
      method("startCallableProcessing")
        .and(withArgument(0, classOf[Callable[_]])),
      classOf[CallableWrapper]
    )
}

class DispatchAdvice
object DispatchAdvice {
  @Advice.OnMethodEnter()
  @static def enter(
    @Advice.This dispatcherServlet: HasServerInstrumentation,
    @Advice.Argument(0) request: HttpServletRequest
  ): (RequestHandler, Scope) = {
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
  @static def exit(
    @Advice.Enter enter: (RequestHandler, Scope),
    @Advice.Argument(0) request: HttpServletRequest,
    @Advice.Argument(1) response: HttpServletResponse
  ): Unit = {
    val (handler, scope) = enter

    if (response.isCommitted) {
      handler.buildResponse(InstrumentationUtils.responseBuilder(response), handler.context)
      handler.responseSent()
      request.removeAttribute("kamon-handler")
    }

    scope.close()
  }
}

class GetHandlerAdvice
object GetHandlerAdvice {
  @Advice.OnMethodExit()
  @static def exit(@Advice.Argument(0) request: HttpServletRequest): Unit = {
    val handler = request.getAttribute("kamon-handler").asInstanceOf[RequestHandler]
    val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)

    if (handler != null && pattern != null) {
      handler.span.name(pattern.toString)
      handler.span.takeSamplingDecision()
    }
  }
}

class ProcessHandlerExceptionAdvice
object ProcessHandlerExceptionAdvice {
  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def exit(@Advice.Argument(3) throwable: Throwable): Unit = {
    if (throwable != null) {
      Kamon.currentSpan().fail(throwable)
    }
  }
}

class RenderAdvice
object RenderAdvice {
  @Advice.OnMethodEnter()
  @static def enter(): Span =
    Kamon.internalSpanBuilder("view.render", "spring.server").start()

  @Advice.OnMethodExit()
  @static def exit(@Advice.Enter span: Span): Unit =
    span.finish()
}
