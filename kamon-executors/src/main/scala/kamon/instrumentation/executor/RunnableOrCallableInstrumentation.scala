package kamon.instrumentation.executor

import kamon.instrumentation.context.{CaptureCurrentContext, HasContext, InvokeWithCapturedContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder

/**
  * Captures the current Context upon creation of all Runnable/Callable implementations and sets that Context as current
  * while their run/call methods are executed. See the module's exclude configuration for more info on what packages and
  * implementations will not be targeted by this instrumentation (e.g. it does not target any java.* class by default).
  */
class RunnableOrCallableInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("java.lang.Runnable", "java.util.concurrent.Callable")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, CaptureCurrentContext)
    .advise(anyMethods("run", "call"), InvokeWithCapturedContext)
}
