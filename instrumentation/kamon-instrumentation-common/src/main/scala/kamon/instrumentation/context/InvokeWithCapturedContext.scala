package kamon
package instrumentation
package context

import kamon.context.Storage
import kanela.agent.libs.net.bytebuddy.asm.Advice

/**
  * Advice that sets the Context from a HasContext instance as the current Context while the advised method is invoked.
  */
object InvokeWithCapturedContext {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasContext: HasContext): Storage.Scope =
    Kamon.storeContext(hasContext.context)

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}
