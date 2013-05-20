package akka.instrumentation

import org.aspectj.lang.annotation.{Around, Before, Pointcut, Aspect}
import kamon.TraceContext
import scala.util.Try
import scala.concurrent.ExecutionContext
import org.aspectj.lang.ProceedingJoinPoint

@Aspect("perthis(promiseCreation())")
class PromiseInstrumentation {

  private var traceContext: Option[TraceContext] = None

  @Pointcut("execution(scala.concurrent.impl.Promise.DefaultPromise.new(..))")
  def promiseCreation(): Unit = {}

  @Before("promiseCreation()")
  def catchTheTraceContext = {
    TraceContext.current match {
      case Some(ctx) => traceContext = Some(ctx.fork)
      case None      => traceContext = None
    }
  }

  @Pointcut("execution(* scala.concurrent.Future.onComplete(..)) && args(func, executor)")
  def registeringOnCompleteCallback(func: Try[Any] => Any, executor: ExecutionContext) = {}

  @Around("registeringOnCompleteCallback(func, executor)")
  def around(pjp: ProceedingJoinPoint, func: Try[Any] => Any, executor: ExecutionContext) = {

    val wrappedFunction = traceContext match {
      case Some(ctx) => (tryResult: Try[Any]) => {
        TraceContext.set(ctx)
        val result = func(tryResult)
        TraceContext.clear

        result
      }
      case None => func
    }

    pjp.proceed(pjp.getArgs.updated(0, wrappedFunction))
  }
}
