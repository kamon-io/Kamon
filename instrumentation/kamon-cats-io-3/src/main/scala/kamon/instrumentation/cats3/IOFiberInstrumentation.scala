package kamon.instrumentation.cats3
import cats.effect.kamonCats.PackageAccessor
import kamon.Kamon
import kamon.context.Storage
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static

class IOFiberInstrumentation extends InstrumentationBuilder {

  onType("cats.effect.IOFiber")
    .mixin(classOf[HasContext.Mixin])
    .mixin(classOf[HasStorage.Mixin])
    .advise(isConstructor.and(takesArguments(5).or(takesArguments(3))), AfterFiberInit)
    .advise(method("suspend"), SaveCurrentContextOnSuspend)
    .advise(method("resume"), RestoreContextOnSuccessfulResume)
    .advise(method("run"), RunLoopWithContext)

  // We must save the context on calls to `schedule*`/`reschedule*` because the Fiber
  // might start executing on another thread before the call to `run` on the current
  // thread terminates.
  onTypes("cats.effect.IOFiber")
    .advise(
      anyMethods(
        "rescheduleFiber",
        "scheduleFiber",
        "scheduleOnForeignEC"
      ),
      SetContextOnNewFiber
    )

  onTypes("cats.effect.unsafe.WorkStealingThreadPool")
    .advise(method("sleepInternal"), classOf[CleanSchedulerContextAdvice35]) // > 3.3
    .advise(
      anyMethods(
        "scheduleFiber", // <3.4
        "rescheduleFiber", // <3.4
        "reschedule",
        "scheduleExternal"
      ),
      SetContextOnNewFiberForWSTP
    )

  // For < 3.4 cats, Scheduled actions like `IO.sleep` end up calling `resume` from the scheduler thread,
  // which always leaves a dirty thread. This wrapper ensures that scheduled actions are
  // executed with the same Context that was available when they were scheduled, and then
  // reset the scheduler thread to the empty context.
  onSubTypesOf("cats.effect.unsafe.Scheduler")
    .advise(method("sleep"), classOf[CleanSchedulerContextAdvice])
}

/**
 * Mixin that exposes access to the scope captured by an instrumented instance.
 * The interface exposes means of getting and more importantly closing of the
 * scope.
 */
trait HasStorage {

  /**
   * Returns the [[Storage.Scope]] stored in the instrumented instance.
   */
  def kamonScope: Storage.Scope

  /**
   * Updates the [[Storage.Scope]] stored in the instrumented instance
   */
  def setKamonScope(scope: Storage.Scope): Unit

}

object HasStorage {

  /**
   * [[HasStorage]] implementation that keeps the scope in a mutable field.
   */
  class Mixin(@transient private var _scope: Storage.Scope) extends HasStorage {

    override def kamonScope: Storage.Scope = if (_scope != null) _scope else Storage.Scope.Empty

    override def setKamonScope(scope: Storage.Scope): Unit = _scope = scope
  }
}

class AfterFiberInit
object AfterFiberInit {

  @Advice.OnMethodExit
  @static def exit(@Advice.This fiber: Any): Unit = {
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
  }
}

class RunLoopWithContext
object RunLoopWithContext {

  @Advice.OnMethodEnter()
  @static def enter(@Advice.This fiber: Any): Storage.Scope = {
    val ctxFiber = fiber.asInstanceOf[HasContext].context
    Kamon.storeContext(ctxFiber)
  }

  @Advice.OnMethodExit()
  @static def exit(@Advice.Enter scope: Storage.Scope, @Advice.This fiber: Any): Unit = {
    val leftContext = Kamon.currentContext()
    scope.close()

    fiber.asInstanceOf[HasContext].setContext(leftContext)
  }
}

class RestoreContextOnSuccessfulResume
object RestoreContextOnSuccessfulResume {

  @Advice.OnMethodExit()
  @static def exit(@Advice.This fiber: Any, @Advice.Return wasSuspended: Boolean): Unit = {

    // Resume is tricky, most of the time we want to keep the `wasSuspended` behavior,
    // but there's a single issue with Dispatcher, basically it resumes differently, so
    // we try to catch that case and identify it and do something different with it.
    val dispatcherIsRunningDirectly =
      PackageAccessor.fiberObjectStackBuffer(fiber).exists(PackageAccessor.isDispatcherWorker)

    val fi = fiber.asInstanceOf[HasContext with HasStorage]

    val setContext = wasSuspended && !dispatcherIsRunningDirectly
    if (setContext) {
      fi.setKamonScope(Kamon.storeContext(fi.context))
    } else if (dispatcherIsRunningDirectly) {
      fi.setContext(Kamon.currentContext())
    }
  }
}

class SaveCurrentContextOnSuspend
object SaveCurrentContextOnSuspend {

  @Advice.OnMethodExit()
  @static def exit(@Advice.This fiber: Any): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    fi.setContext(Kamon.currentContext())
  }
}

class CleanFiberUp
object CleanFiberUp {

  @Advice.OnMethodExit()
  @static def exit(@Advice.This fiber: Any): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    fi.kamonScope.close()
  }
}

class SetContextOnNewFiber
object SetContextOnNewFiber {

  @Advice.OnMethodEnter()
  @static def enter(@Advice.Argument(1) fiber: Any): Unit =
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
}

class SetContextOnNewFiberForWSTP
object SetContextOnNewFiberForWSTP {

  @Advice.OnMethodEnter()
  @static def enter(@Advice.Argument(0) fiber: Any): Unit =
    fiber.asInstanceOf[HasContext].setContext(Kamon.currentContext())
}
