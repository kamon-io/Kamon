package kamon.instrumentation.cats

import cats.effect.{IO, IOLocal}
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice


class IOFiberInstrumentation extends InstrumentationBuilder {

  /**Approach: RunLoop Instrumentation**/
  //onTypes("cats.effect.IOFiber")
  //  .advise(anyMethods(
  //    "runLoop",
  //  ), InstrumentRunLoop)
  /**Approach: RunLoop Instrumentation**/



  /**Approach: Instrumenting run() and "forks" **/
  onTypes("cats.effect.IOFiber")
    .advise(method("run"), RestoreContextFromFiber)
    .advise(method("run"), SaveCurrentContextOnExit)

  onTypes("cats.effect.IOFiber")
    .advise(anyMethods(
      "rescheduleFiber",
      "scheduleFiber",
      "scheduleOnForeignEC",
    ), SetContextOnNewFiber)
  onTypes("cats.effect.unsafe.WorkStealingThreadPool")
    .advise(anyMethods("scheduleFiber", "rescheduleFiber", "scheduleExternal"),SetContextOnNewFiberForWSTP)
  /**Approach: More efficient solution**/



  /**Debug: begin**/
  onTypes("cats.effect.IOFiber")
    .advise(anyMethods(
      "runLoop",
    ), Debug)
  /**Debug: end**/

}
object Helper {
  def padTo(obj: Any, len: Int): String =
    obj.toString.take(len).padTo(len, " ").mkString("")

  def setIfNotEmpty(f: HasContext)(ctx: Context): Unit =
    if(ctx.nonEmpty()){
      f.setContext(ctx)
    }

  def setCurrentCtxIfNotEmpty(ctx: Context): Unit =
    if(ctx.nonEmpty()){
      Kamon.storeContext(ctx)
    }
}
import Helper._


object RestoreContextFromFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"run(enter)     | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val ctxFiber = fiber.asInstanceOf[HasContext].context
    setCurrentCtxIfNotEmpty(ctxFiber)
  }
}

object SaveCurrentContextOnExit {
  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"run(exit)      | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    setIfNotEmpty(fiber.asInstanceOf[HasContext])(currentCtx)
  }
}


object SetContextOnNewFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This currFiber: Any, @Advice.Argument(1) fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"ScheduleNew    | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(currFiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo(fiber.hashCode(), 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    setIfNotEmpty(fiber.asInstanceOf[HasContext])(currentCtx)
  }
}

object SetContextOnNewFiberForWSTP {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.Argument(0) fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"ScheduleNew    | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo("unknown", 10)} | ToBeScheduledFiberId: ${padTo(fiber.hashCode(), 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    setIfNotEmpty(fiber.asInstanceOf[HasContext])(currentCtx)
  }
}

object InstrumentRunLoop {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"run(enter)     | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val ctxFiber = fiber.asInstanceOf[HasContext].context
    Kamon.storeContext(ctxFiber)
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"run(exit)      | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: {${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    fiber.asInstanceOf[HasContext].setContext(currentCtx)
  }
}

object Debug {

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any, @Advice.Argument(0) io :Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"runLoop(Enter) | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(io.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any, @Advice.Argument(0) io :Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"runLoop(Exit)  | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo(fiber.hashCode(), 10)} | ToBeScheduledFiberId: ${padTo("NA", 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(io.getClass.getCanonicalName, 25)} | Thread ${Thread.currentThread().getName}")
  }
}

object DebugWT {

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.Argument(0) fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"WorkerThread   | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo("undefined", 10)} | ToBeScheduledFiberId: ${padTo(fiber.hashCode(), 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(-1, 25)} | Thread ${Thread.currentThread().getName}")
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.Argument(0) fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"WorkerThread   | Resume Tag: ${field.get(fiber)} | CurrFiberId: ${padTo("undefined", 10)} | ToBeScheduledFiberId: ${padTo(fiber.hashCode(), 10)} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(-1, 25)} | Thread ${Thread.currentThread().getName}")
  }
}
