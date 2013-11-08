package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.{ContextAware, TraceContext, Trace}

@Aspect
class RunnableTracing {

  /**
   *  These are the Runnables that need to be instrumented and make the TraceContext available
   *  while their run method is executed.
   */
  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def onCompleteCallbacksRunnable: ContextAware = ContextAware.default


  /**
   *  Pointcuts
   */

  @Pointcut("execution(kamon.trace.ContextAware+.new(..)) && this(runnable)")
  def instrumentedRunnableCreation(runnable: ContextAware): Unit = {}

  @Pointcut("execution(* kamon.trace.ContextAware+.run()) && this(runnable)")
  def runnableExecution(runnable: ContextAware) = {}



  @After("instrumentedRunnableCreation(runnable)")
  def beforeCreation(runnable: ContextAware): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }


  @Around("runnableExecution(runnable)")
  def around(pjp: ProceedingJoinPoint, runnable: ContextAware): Any = {
    import pjp._

    Trace.traceContext.withValue(runnable.traceContext) {
      proceed()
    }
  }

}