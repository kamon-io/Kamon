package kamon.instrumentation.executor

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.executor.ContextAware.{
  ContextAwareCallable,
  ContextAwareCallableProvider,
  ContextAwareRunnable,
  ContextAwareRunnableProvider
}

import java.util.concurrent.Callable

class TestContextAwareRunnable(private val underlying: Runnable, private val ctx: Context)
    extends ContextAwareRunnable {
  override def getUnderlying: Runnable = underlying
  override def getContext: Context = ctx
}

class TestContextAwareCallable[A](private val underlying: Callable[A], private val ctx: Context)
    extends ContextAwareCallable[A] {

  override def getUnderlying: Callable[A] = underlying

  override def getContext: Context = ctx
}

class TestContextAwareRunnableProvider extends ContextAwareRunnableProvider {

  override def provide(original: Runnable): ContextAwareRunnable =
    new TestContextAwareRunnable(original, Kamon.currentContext())

  override def test(r: Runnable): Boolean = r.isInstanceOf[SimpleRunnable]
}

class TestContextAwareCallableProvider extends ContextAwareCallableProvider {

  override def provide[A](original: Callable[A]): ContextAwareCallable[A] =
    new TestContextAwareCallable(original, Kamon.currentContext())

  override def test(r: Callable[_]): Boolean = r.isInstanceOf[SimpleCallable]
}
