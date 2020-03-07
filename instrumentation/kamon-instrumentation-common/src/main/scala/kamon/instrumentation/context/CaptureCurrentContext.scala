package kamon
package instrumentation
package context

import kanela.agent.libs.net.bytebuddy.asm.Advice

/**
  * Advise that copies the current Context from Kamon into a HasContext instance when the advised method starts
  * executing.
  */
object CaptureCurrentContextOnEnter {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasContext: HasContext): Unit =
    hasContext.setContext(Kamon.currentContext())

}

/**
  * Advise that copies the current Context from Kamon into a HasContext instance when the advised method finishes
  * executing.
  */
object CaptureCurrentContextOnExit {

  @Advice.OnMethodExit
  def exit(@Advice.This hasContext: HasContext): Unit =
    hasContext.setContext(Kamon.currentContext())

}
