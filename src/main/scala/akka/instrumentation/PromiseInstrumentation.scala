package akka.instrumentation

import org.aspectj.lang.annotation.{Around, Before, Pointcut, Aspect}
import kamon.TraceContext
import scala.util.Try
import java.util.concurrent.ExecutorService
import scala.concurrent.ExecutionContext
import org.aspectj.lang.ProceedingJoinPoint

@Aspect("perthis(promiseCreation())")
class PromiseInstrumentation {

  println("Created an instrumented promise")

  private var traceContext: Option[TraceContext] = None

  @Pointcut("execution(scala.concurrent.impl.Promise.DefaultPromise.new(..))")
  def promiseCreation(): Unit = {}

  @Before("promiseCreation()")
  def catchTheTraceContext = {
    TraceContext.current match {
      case Some(ctx) => traceContext = Some(ctx.fork)
      case None => traceContext = None
    }
    println(s"-----------------> Created a Promise, now the context is:$traceContext")
  }

  @Pointcut("execution(* scala.concurrent.Future.onComplete(..)) && args(func, executor)")
  def registeringOnCompleteCallback(func: Try[Any] => Any, executor: ExecutionContext) = {}

  @Around("registeringOnCompleteCallback(func, executor)")
  def around(pjp: ProceedingJoinPoint, func: Try[Any] => Any, executor: ExecutionContext) = {
    println("Someone registered a callback")

    val wrappedFunction = traceContext match {
      case Some(ctx) => (f: Try[Any]) => {
        println("Wrapping some context")
        TraceContext.set(ctx)
        val result = func
        TraceContext.clear

        result
      }
      case None => println("No Context at all"); func
    }
    println("Proceeding to the JP")
    pjp.proceed(pjp.getArgs.updated(0, wrappedFunction))
  }

}
