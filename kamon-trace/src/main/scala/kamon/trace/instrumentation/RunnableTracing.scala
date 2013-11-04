package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import kamon.trace.TraceContext

@Aspect
class RunnableTracing {

  /**
   *  These are the Runnables that need to be instrumented and make the TraceContext available
   *  while their run method is executed.
   */
  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def onCompleteCallbacksRunnable: TraceContextAwareRunnable = new TraceContextAwareRunnable {
    val traceContext: Option[TraceContext] = Tracer.traceContext.value
  }


  /**
   *  Pointcuts
   */

  @Pointcut("execution(kamon.instrumentation.TraceContextAwareRunnable+.new(..)) && this(runnable)")
  def instrumentedRunnableCreation(runnable: TraceContextAwareRunnable): Unit = {}

  @Pointcut("execution(* kamon.instrumentation.TraceContextAwareRunnable+.run()) && this(runnable)")
  def runnableExecution(runnable: TraceContextAwareRunnable) = {}



  @After("instrumentedRunnableCreation(runnable)")
  def beforeCreation(runnable: TraceContextAwareRunnable): Unit = {
    // Force traceContext initialization.
    runnable.traceContext
  }


  @Around("runnableExecution(runnable)")
  def around(pjp: ProceedingJoinPoint, runnable: TraceContextAwareRunnable): Any = {
    import pjp._

    Tracer.traceContext.withValue(runnable.traceContext) {
      proceed()
    }
  }

}

/**
 *  Marker interface, just to make sure we don't instrument all the Runnables in the classpath.
 */
trait TraceContextAwareRunnable {
  def traceContext: Option[TraceContext]
}