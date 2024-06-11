package kamon.instrumentation.zio2

import kamon.Kamon
import kamon.context.Storage
import kamon.instrumentation.context.HasContext
import kanela.agent.api.instrumentation.InstrumentationBuilder
import zio.{Exit, Fiber, Supervisor, UIO, Unsafe, ZEnvironment, ZIO}

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
    .mixin(classOf[HasStorage.Mixin])

  onType("zio.Runtime$")
    .advise(method("defaultSupervisor"), classOf[SupervisorAdvice.OverrideDefaultSupervisor])
}

/**
 * Mixin that exposes access to the scope captured by an instrumented instance. The interface exposes means of getting and more importantly
 * closing of the scope.
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

class NewSupervisor extends Supervisor[Any] {

  override def value(implicit trace: zio.Trace): UIO[Any] = ZIO.unit

  override def onStart[R, E, A_](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A_],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A_]
  )(implicit unsafe: Unsafe): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    fi.setContext(Kamon.currentContext())
  }

  override def onSuspend[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    fi.setContext(Kamon.currentContext())
    fi.kamonScope.close()
  }

  override def onResume[E, A_](fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    val ctx = fi.context
    fi.setKamonScope(Kamon.storeContext(ctx))
  }

  override def onEnd[R, E, A_](value: Exit[E, A_], fiber: Fiber.Runtime[E, A_])(implicit unsafe: Unsafe): Unit = {
    val fi = fiber.asInstanceOf[HasContext with HasStorage]
    fi.kamonScope.close()
    ()
  }
}
