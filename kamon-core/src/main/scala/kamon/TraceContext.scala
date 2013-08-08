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
  implicit val as2 = Kamon.actorSystem.dispatcher
  def apply()(implicit actorSystem: ActorSystem) = new TraceContext(UUID.randomUUID(), Agent[List[TraceEntry]](Nil))
}



trait TraceEntry

case class CodeBlockExecutionTime(name: String, begin: Long, end: Long) extends TraceEntry



case class TransactionTrace(id: UUID, start: Long, end: Long, entries: Seq[TraceEntry])





object Collector {

}

trait TraceEntryStorage {
  def store(entry: TraceEntry): Boolean
}

class TransactionContext(val id: UUID, private val storage: TraceEntryStorage) {
  def store(entry: TraceEntry) = storage.store(entry)
}

object ThreadLocalTraceEntryStorage extends TraceEntryStorage {

  private val storage = new ThreadLocal[List[TraceEntry]] {
    override def initialValue(): List[TraceEntry] = Nil
  }

  def update(f: List[TraceEntry] => List[TraceEntry]) = storage set f(storage.get)

  def store(entry: TraceEntry): Boolean = {
    update(entry :: _)
    true
  }
}


