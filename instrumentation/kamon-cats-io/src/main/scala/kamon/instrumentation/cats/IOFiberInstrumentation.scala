package kamon.instrumentation.cats

import kamon.Kamon
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice


class IOFiberInstrumentation extends InstrumentationBuilder {
  onTypes("cats.effect.IOFiber")
    .advise(method("run"), IOFiberInstrumentation)
}

object IOFiberInstrumentation {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    Kamon.storeContext(fiber.asInstanceOf[HasContext].context)
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
    Kamon.storeContext(null)
  }
}
