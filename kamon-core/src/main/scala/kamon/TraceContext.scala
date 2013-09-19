package kamon

import java.util.UUID
import akka.actor._
import java.util.concurrent.atomic.AtomicLong
import kamon.trace.UowTraceAggregator
import scala.concurrent.duration._
import kamon.newrelic.NewRelicReporting
import kamon.trace.UowTracing.Start

// TODO: Decide if we need or not an ID, generating it takes time and it doesn't seem necessary.
case class TraceContext(id: Long, entries: ActorRef, userContext: Option[Any] = None) {
  //implicit val timeout = Timeout(30, TimeUnit.SECONDS)
  implicit val as = Kamon.actorSystem.dispatcher

  def append(entry: TraceEntry) = entries ! entry
  def close = entries ! "Close" // TODO type this thing!.
}

object TraceContext {
  val reporter = Kamon.actorSystem.actorOf(Props[NewRelicReporting])
  val traceIdCounter = new AtomicLong

  def apply()(implicit system: ActorSystem) =  {
    val actor = system.actorOf(UowTraceAggregator.props(reporter, 30 seconds), s"tracer-${traceIdCounter.incrementAndGet()}")
    actor ! Start()

    new TraceContext(100, actor) // TODO: Move to a kamon specific supervisor, like /user/kamon/tracer
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


