package kamon

/**
 *  Provides support for making a TraceContext available as ThreadLocal and cleanning up afterwards.
 */
trait TraceContextSwap {

  def withContext[A](ctx: Option[TraceContext], body: => A): A = withContext(ctx, body, body)

  def withContext[A](ctx: Option[TraceContext], primary: => A, fallback: => A): A = {
    ctx match {
      case Some(context) => {
        Kamon.set(context)
        val bodyResult = primary
        Kamon.clear

        bodyResult
      }
      case None => fallback
    }

  }

}

object TraceContextSwap extends TraceContextSwap
