package kamon.instrumentation

import org.aspectj.lang.annotation._
import kamon.{Tracer, TraceContext}
import org.aspectj.lang.ProceedingJoinPoint
import scala.Some

/**
 *  Marker interface, just to make sure we don't instrument all the Runnables in the classpath.
 */
trait TraceContextAwareRunnable {
  def traceContext: Option[TraceContext]
}


@Aspect
class RunnableInstrumentation {

  /**
   *  These are the Runnables that need to be instrumented and make the TraceContext available
   *  while their run method is executed.
   */
  @DeclareMixin("scala.concurrent.impl.CallbackRunnable || scala.concurrent.impl.Future.PromiseCompletingRunnable")
  def onCompleteCallbacksRunnable: TraceContextAwareRunnable = new TraceContextAwareRunnable {
    val traceContext: Option[TraceContext] = Tracer.context()
  }


  /**
   *  Pointcuts
   */

  @Pointcut("execution(kamon.instrumentation.TraceContextAwareRunnable+.new(..)) && this(runnable)")
  def instrumentedRunnableCreation(runnable: TraceContextAwareRunnable): Unit = {}

  @Pointcut("execution(* kamon.instrumentation.TraceContextAwareRunnable+.run()) && this(runnable)")
  def runnableExecution(runnable: TraceContextAwareRunnable) = {}



  import kamon.TraceContextSwap.withContext

  @After("instrumentedRunnableCreation(runnable)")
  def beforeCreation(runnable: TraceContextAwareRunnable) = {
    val x = runnable.traceContext
    /*if(runnable.traceContext.isEmpty)
      println("WTFWI from: " + (new Throwable).getStackTraceString)
    else
      println("NOWTF: " + (new Throwable).getStackTraceString)*/
  /*  if(traceContext.isEmpty)
      println("NO TRACE CONTEXT FOR RUNNABLE at: [[[%s]]]", (new Throwable).getStackTraceString)//println((new Throwable).getStackTraceString)
    else
      println("SUPER TRACE CONTEXT FOR RUNNABLE at: [[[%s]]]", (new Throwable).getStackTraceString)*/
  }


  @Around("runnableExecution(runnable)")
  def around(pjp: ProceedingJoinPoint, runnable: TraceContextAwareRunnable) = {
    import pjp._

    /*println("EXECUTING")
    if(runnable.traceContext.isEmpty)
      println("NOMONEY")

    runnable.traceContext match {
      case Some(context) => {
        //MDC.put("uow", context.userContext.get.asInstanceOf[String])
        Tracer.set(context)
        val bodyResult = proceed()
        Tracer.clear
        //MDC.remove("uow")

        bodyResult
      }
      case None => proceed()
    }*/
    withContext(runnable.traceContext, proceed())
  }

}

