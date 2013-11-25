package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.{ContextAware, TraceContext, Trace}

@Aspect
class FutureTracing {

  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def mixin: ContextAware = ContextAware.default


  @Pointcut("execution((scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).new(..)) && this(runnable)")
  def futureRelatedRunnableCreation(runnable: ContextAware): Unit = {}

  @After("futureRelatedRunnableCreation(runnable)")
  def afterCreation(runnable: ContextAware): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }


  @Pointcut("execution(* (scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable).run()) && this(runnable)")
  def futureRelatedRunnableExecution(runnable: ContextAware) = {}

  @Around("futureRelatedRunnableExecution(runnable)")
  def aroundExecution(pjp: ProceedingJoinPoint, runnable: ContextAware): Any = {
    Trace.withContext(runnable.traceContext) {
      pjp.proceed()
    }
  }

}