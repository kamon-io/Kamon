package kamon.instrumentation.zio2

import kamon.Kamon
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import zio.{Exit, Fiber, Supervisor, UIO, Unsafe, ZEnvironment, ZIO}

import scala.annotation.static

/**
 * This works as follows.
 *  - patches the defaultSupervisor val from Runtime to add our own supervisor.
 *  - Mixes in the [[HasContext.Mixin]] class so we don't have to keep a separate map of Fiber -> Context
 *  - Performs context shifting based on starting/suspending of fibers.
 *
 */
class ZIO2Instrumentation extends InstrumentationBuilder {

  onType("zio.internal.FiberRuntime")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, AfterFiberRuntimeInit)

  onType("zio.Runtime$")
    .advise(method("defaultSupervisor"), classOf[SupervisorAdvice.OverrideDefaultSupervisor])
}

class AfterFiberRuntimeInit

object AfterFiberRuntimeInit  {

  @Advice.OnMethodExit
  @static def exit(@Advice.This fiber: Any): Unit ={
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
  }

}


class NewSupervisor extends Supervisor[Any] {

  override def value(implicit trace: zio.Trace): UIO[Any] = ZIO.unit

  override def onStart[R, E, A_](environment: ZEnvironment[R], effect: ZIO[R, E, A_], parent: Option[Fiber.Runtime[Any, Any]], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val ctx = fiber.asInstanceOf[HasContext].context
    Kamon.storeContext(ctx)
  }

  override def onSuspend[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val pctx = Kamon.currentContext()
    fiber.asInstanceOf[HasContext].setContext(pctx)
  }

  override def onResume[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val ctx = fiber.asInstanceOf[HasContext].context
    Kamon.storeContext(ctx)
  }

  override def onEnd[R, E, A_](value: Exit[E, A_], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = ()
}
