package kamon

import akka.actor.{Props, ActorSystem}

object Kamon {

  val ctx = new ThreadLocal[Option[TraceContext]] {
    override def initialValue() = None
  }
  
  implicit lazy val actorSystem = ActorSystem("kamon")


  def context() = ctx.get()
  def clear = ctx.remove()
  def set(traceContext: TraceContext) = ctx.set(Some(traceContext))

  def start = set(newTraceContext)
  def stop = ctx.get match {
    case Some(context) => context.close
    case None =>
  }

  def newTraceContext(): TraceContext = TraceContext()


  val publisher = actorSystem.actorOf(Props[TransactionPublisher])

  def publish(tx: FullTransaction) = publisher ! tx

}



object Tracer {
  val ctx = new ThreadLocal[Option[TraceContext]] {
    override def initialValue() = None
  }

  def context() = ctx.get()
  def clear = ctx.remove()
  def set(traceContext: TraceContext) = ctx.set(Some(traceContext))

  def start = ??? //set(newTraceContext)
  def stop = ctx.get match {
    case Some(context) => context.close
    case None =>
  }

  //def newTraceContext(): TraceContext = TraceContext()

}
