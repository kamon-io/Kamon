package kamon

import java.util.UUID
import akka.actor.{ActorSystem, ActorPath}
import akka.agent.Agent
import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}
import akka.util.Timeout


case class TraceContext(id: UUID, private val entries: Agent[List[TraceEntry]], userContext: Option[Any] = None) {
  implicit val timeout = Timeout(30, TimeUnit.SECONDS)
  implicit val as = Kamon.actorSystem.dispatcher

  def append(entry: TraceEntry) = entries send (entry :: _)
  def close = entries.future.onComplete({
    case Success(list) => Kamon.publish(FullTransaction(id, list))
    case Failure(t) => println("WTF!")
  })
}

object TraceContext {
  def apply()(implicit actorSystem: ActorSystem) = new TraceContext(UUID.randomUUID(), Agent[List[TraceEntry]](Nil))
}



trait TraceEntry

case class CodeBlockExecutionTime(name: String, begin: Long, end: Long) extends TraceEntry
