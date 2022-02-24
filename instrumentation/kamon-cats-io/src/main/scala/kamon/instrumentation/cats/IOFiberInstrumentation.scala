package kamon.instrumentation.cats

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice


class IOFiberInstrumentation extends InstrumentationBuilder {
  onTypes("cats.effect.IOFiber")
    .advise(method("run"), RestoreContextFromFiber)
    .advise(method("run"), SaveCurrentContextOnExit)

  onTypes("cats.effect.IOFiber")
    .advise(anyMethods(
      "rescheduleFiber",
      "scheduleFiber",
      "scheduleOnForeignEC",
    ), SetContextOnNewFiber)

/*  onTypes("cats.effect.IOFiber")
    .advise(anyMethods(
      "runLoop",
    ), Debug)

  onTypes("cats.effect.unsafe.WorkerThread")
    .advise(anyMethods("schedule", "reschedule"), DebugWT)*/

  /*  onTypes("cats.effect.IOFiber")
      .advise(method("run"), IOFiberInstrumentationSetOnRun)
      .advise(method("run"), IOFiberInstrumentationSaveCurrentOnExit)


    onTypes("cats.effect.IOFiber")
      .advise(anyMethods(
        "rescheduleFiber",
        "scheduleFiber",
        "scheduleOnForeignEC",
      ), IOFiberInstrumentationSaveCurrentOnExit)

*/


}
/*object Helper {
  def padTo(obj: Any, len: Int): String =
    obj.toString.take(len).padTo(len, " ").mkString("")

}
import Helper._*/


object RestoreContextFromFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"RestoreCtx(run)| Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Cancellation: ${padTo(0, 5)} | Autocede: ${0} | Thread ${Thread.currentThread().getName}")
    val ctxFiber = fiber.asInstanceOf[HasContext].context
    if(ctxFiber.nonEmpty()){
      Kamon.storeContext(ctxFiber)
    }
  }
}

object SaveCurrentContextOnExit {
  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"SaveCtx        | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Cancellation: ${padTo(0, 5)} | Autocede: ${0} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    if(currentCtx.nonEmpty()){
      fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
    }
    //Kamon.storeContext(null)
  }
}

object SetContextOnNewFiber {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.Argument(1) fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    //println(s"SetOnFiber     | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(fiber.getClass.getCanonicalName, 25)} | Cancellation: ${padTo(0, 5)} | Autocede: ${0} | Thread ${Thread.currentThread().getName}")
    val currentCtx = Kamon.currentContext()
    if(currentCtx.nonEmpty()){
      fiber.asInstanceOf[HasContext].setContext(currentCtx)
    }

  }
}







/*
object Debug {

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any, @Advice.Argument(0) io :Any,@Advice.Argument(1) cancellation: Int, @Advice.Argument(2) autoCede: Int): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    println(s"RunLoop(Enter) | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(io.getClass.getCanonicalName, 25)} | Cancellation: ${padTo(cancellation, 5)} | Autocede: ${autoCede} | Thread ${Thread.currentThread().getName}")
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any, @Advice.Argument(0) io :Any,@Advice.Argument(1) cancellation: Int, @Advice.Argument(2) autoCede: Int): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    println(s"RunLoop(Exit)  | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(io.getClass.getCanonicalName, 25)} | Cancellation: ${padTo(cancellation, 5)} | Autocede: ${autoCede} | Thread ${Thread.currentThread().getName}")
  }
}

object DebugWT {

  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def enter(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    println(s"WorkerThread    | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(0, 25)} | Cancellation: ${padTo(0, 5)} | Autocede: ${0} | Thread ${Thread.currentThread().getName}")
  }

  @Advice.OnMethodExit(suppress = classOf[Throwable])
  def exit(@Advice.This fiber: Any): Unit = {
    val field = fiber.getClass.getDeclaredField("resumeTag")
    field.setAccessible(true)
    println(s"WorkerThread  | Resume Tag: ${field.get(fiber)} | FiberId: ${fiber.hashCode()} | Fiber: ${padTo(fiber.asInstanceOf[HasContext].context.tags, 15)} | Thread ${padTo(Kamon.currentContext().tags, 15)} | IO: ${padTo(0, 25)} | Cancellation: ${padTo(0, 5)} | Autocede: ${0} | Thread ${Thread.currentThread().getName}")
    Kamon.storeContext(fiber.asInstanceOf[HasContext].context)
  }
}*/
