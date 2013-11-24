package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.{ContextAware, TraceContext, Trace}

@Aspect
class FutureTracing {

  /**
   *  These are the Runnables that need to be instrumented and make the TraceContext available
   *  while their run method is executed.
   */
  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def onCompleteCallbacksRunnable: ContextAware = ContextAware.default


  @Pointcut("execution(kamon.trace.ContextAware+.new(..)) && this(runnable)")
  def instrumentedRunnableCreation(runnable: ContextAware): Unit = {}

  @Pointcut("execution(* kamon.trace.ContextAware+.run()) && this(runnable)")
  def futureRunnableExecution(runnable: ContextAware) = {}


  @After("instrumentedRunnableCreation(runnable)")
  def beforeCreation(runnable: ContextAware): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }

  @Around("futureRunnableExecution(runnable)")
  def around(pjp: ProceedingJoinPoint, runnable: ContextAware): Any = {
    import pjp._

    Trace.withValue(runnable.traceContext) {
      proceed()
    }
  }

}