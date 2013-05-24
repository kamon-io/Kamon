package kamon.instrumentation

import org.aspectj.lang.annotation.{Around, Before, Pointcut, Aspect}
import kamon.TraceContext
import org.aspectj.lang.ProceedingJoinPoint

@Aspect("perthis(promiseCompletingRunnableCreation())")
class PromiseCompletingRunnableInstrumentation {

  private var traceContext: Option[TraceContext] = None

  @Pointcut("execution(scala.concurrent.impl.Future.PromiseCompletingRunnable.new(..))")
  def promiseCompletingRunnableCreation(): Unit = {}

  @Before("promiseCompletingRunnableCreation()")
  def catchTheTraceContext = {
    TraceContext.current match {
      case Some(ctx) => traceContext = Some(ctx.fork)
      case None      => traceContext = None
    }
  }


  @Pointcut("execution(* scala.concurrent.impl.Future.PromiseCompletingRunnable.run())")
  def runnableExecution() = {}

  @Around("runnableExecution()")
  def around(pjp: ProceedingJoinPoint) = {
    import pjp._

    traceContext match {
      case Some(ctx) => {
        TraceContext.set(ctx)
        proceed()
        TraceContext.clear
      }
      case None => proceed()
    }
  }

}
