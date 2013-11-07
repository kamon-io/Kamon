package kamon.trace

import java.util.UUID
import akka.actor._
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._
import kamon.Kamon
import kamon.trace.UowTracing.{Finish, Start}

// TODO: Decide if we need or not an ID, generating it takes time and it doesn't seem necessary.
protected[kamon] case class TraceContext(private val collector: ActorRef, id: Long, uow: String = "", userContext: Option[Any] = None) {

  def start(name: String) = collector ! Start(id, name)

  def finish: Unit = {
    collector ! Finish(id)
  }


}


trait ContextAware {
  def traceContext: Option[TraceContext]
}

object ContextAware {
  def default: ContextAware = new ContextAware {
    val traceContext: Option[TraceContext] = Trace.context()
  }
}

trait TimedContextAware {
  def timestamp: Long
  def traceContext: Option[TraceContext]
}
