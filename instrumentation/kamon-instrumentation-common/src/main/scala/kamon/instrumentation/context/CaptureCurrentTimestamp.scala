package kamon.instrumentation.context

import kanela.agent.libs.net.bytebuddy.asm.Advice

/**
  * Advise that copies the current System.nanoTime into a HasTimestamp instance when the advised method starts
  * executing.
  */
object CaptureCurrentTimestampOnEnter {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasTimestamp: HasTimestamp): Unit =
    hasTimestamp.setTimestamp(System.nanoTime())

}

/**
  * Advise that copies the current System.nanoTime into a HasTimestamp instance when the advised method finishes
  * executing.
  */
object CaptureCurrentTimestampOnExit {

  @Advice.OnMethodExit
  def exit(@Advice.This hasTimestamp: HasTimestamp): Unit =
    hasTimestamp.setTimestamp(System.nanoTime())

}
