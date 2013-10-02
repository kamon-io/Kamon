package kamon

import org.slf4j.MDC

/**
 *  Provides support for making a TraceContext available as ThreadLocal and cleanning up afterwards.
 */
trait TraceContextSwap {

  def withContext[A](ctx: Option[TraceContext], body: => A): A = withContext(ctx, body, body)

  def withContext[A](ctx: Option[TraceContext], primary: => A, fallback: => A): A = {

    val previous = Tracer.context()
    val r = ctx match {
      case Some(context) => {
        //MDC.put("uow", context.userContext.get.asInstanceOf[String])
        Tracer.set(context)
        val bodyResult = primary
        //Tracer.clear
        //MDC.remove("uow")

        bodyResult
      }
      case None => fallback
    }
    previous.map(ctx => Tracer.set(ctx))

    r
  }

}

object TraceContextSwap extends TraceContextSwap
