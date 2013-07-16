package kamon

import akka.actor.{Props, ActorSystem}
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import kamon.metric.{Atomic, ActorSystemMetrics}

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



  object Metric {
    val actorSystems = new ConcurrentHashMap[String, ActorSystemMetrics] asScala

    def actorSystemNames: List[String] = actorSystems.keys.toList
    def registerActorSystem(name: String) = actorSystems.getOrElseUpdate(name, ActorSystemMetrics(name))

    def actorSystem(name: String): Option[ActorSystemMetrics] = actorSystems.get(name)
  }

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
