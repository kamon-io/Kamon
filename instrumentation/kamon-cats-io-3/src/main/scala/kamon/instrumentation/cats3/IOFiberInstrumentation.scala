package kamon.instrumentation.cats3

import kamon.Kamon
import kamon.context.Storage
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice


class IOFiberInstrumentation extends InstrumentationBuilder {

  onTypes("cats.effect.IOFiber")
    .advise(method("run"), RestoreContextFromFiber)
    .advise(method("resume"), RestoreContextOnSuccessfulResume)
    .advise(method("run"), SaveCurrentContextOnExit)
    .advise(method("suspend"), SaveCurrentContextOnExit)

  onTypes("cats.effect.IOFiber")
    .advise(anyMethods(
      "rescheduleFiber",
      "scheduleFiber",
      "scheduleOnForeignEC",
    ), SetContextOnNewFiber)
  onTypes("cats.effect.unsafe.WorkStealingThreadPool")
    .advise(anyMethods("scheduleFiber", "rescheduleFiber", "scheduleExternal"), SetContextOnNewFiberForWSTP)
}

object Helper {
  val currentScope = new ThreadLocal[Storage.Scope]
}

import Helper._


object RestoreContextFromFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    val ctxFiber = fiber.asInstanceOf[HasContext].context
    currentScope.set(Kamon.storeContext(ctxFiber))
  }
}

object RestoreContextOnSuccessfulResume {
  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any, @Advice.Return ret: Any): Unit =
    if (ret.asInstanceOf[Boolean]) {
      val ctxFiber = fiber.asInstanceOf[HasContext].context
      currentScope.set(Kamon.storeContext(ctxFiber))
    }
}

object SaveCurrentContextOnExit {
  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
    Option(currentScope.get()).foreach(_.close())
    currentScope.remove()
  }
}


object SetContextOnNewFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This currFiber: Any, @Advice.Argument(1) fiber: Any): Unit =
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
}

object SetContextOnNewFiberForWSTP {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.Argument(0) fiber: Any): Unit = {
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
  }
}
