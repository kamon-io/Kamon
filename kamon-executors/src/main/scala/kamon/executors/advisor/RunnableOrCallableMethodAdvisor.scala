package kamon.executors.advisor

import kamon.Kamon
import kamon.context.Storage
import kamon.context.Storage.Scope
import kamon.executors.mixin.ContextAware
import kanela.agent.libs.net.bytebuddy.asm.Advice

/**
  *
  */
class RunnableOrCallableMethodAdvisor
object RunnableOrCallableMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This runnable: ContextAware):Storage.Scope =
    Kamon.storeContext(runnable.getContext)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Scope):Unit =
    scope.close()
}