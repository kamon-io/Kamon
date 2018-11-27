package kamon.executors.instrumentation

import kamon.executors.advisor.RunnableOrCallableMethodAdvisor
import kamon.executors.mixin.ContextAwareMixin
import kanela.agent.scala.KanelaInstrumentation


class RunnableOrCallableInstrumentation extends KanelaInstrumentation {

  /**
    *
    */
  forSubtypeOf("java.lang.Runnable" or "java.util.concurrent.Callable") { builder =>
    builder
      .withMixin(classOf[ContextAwareMixin])
      .withAdvisorFor(anyMethod("run", "call"), classOf[RunnableOrCallableMethodAdvisor])
      .build()
  }
}
