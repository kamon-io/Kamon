package kamon.trace

import java.util.UUID
import akka.actor._
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration._

// TODO: Decide if we need or not an ID, generating it takes time and it doesn't seem necessary.
case class TraceContext(id: Long, tracer: ActorRef, uow: String = "", userContext: Option[Any] = None)

object TraceContext {

  def apply()(implicit system: ActorSystem) =  {
    val n = traceIdCounter.incrementAndGet()
    val actor = system.actorOf(UowTraceAggregator.props(reporter, 30 seconds), s"tracer-${n}")
    actor ! Start()

    new TraceContext(n, actor) // TODO: Move to a kamon specific supervisor, like /user/kamon/tracer
  }
}



class TraceAccumulator extends Actor {
  def receive = {
    case a  => println("Trace Accumulated: "+a)
  }
}


trait TraceEntry
case class CodeBlockExecutionTime(name: String, begin: Long, end: Long) extends TraceEntry
case class TransactionTrace(id: UUID, start: Long, end: Long, entries: Seq[TraceEntry])

object Collector

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


