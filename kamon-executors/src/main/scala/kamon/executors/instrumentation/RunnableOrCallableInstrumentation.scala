package kamon.executors.instrumentation

import kamon.executors.advisor.RunnableOrCallableMethodAdvisor
import kamon.executors.mixin.ContextAwareMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder


class RunnableOrCallableInstrumentation extends InstrumentationBuilder {

  /**
    *
    */
  onSubTypesOf("java.lang.Runnable" or "java.util.concurrent.Callable")
    .mixin(classOf[ContextAwareMixin])
    .advise(anyMethods("run", "call"), classOf[RunnableOrCallableMethodAdvisor])
}
