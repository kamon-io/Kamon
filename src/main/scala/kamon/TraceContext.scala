package kamon

import java.util.UUID
import akka.actor.ActorPath


case class TraceContext(id: UUID, entries: List[TraceEntry]) {
  def fork = this.copy(entries = Nil)
  def withEntry(entry: TraceEntry) = this.copy(entries = entry :: entries)
}

object TraceContext {
  private val context = new ThreadLocal[TraceContext]

  def current = {
    val ctx = context.get()
    if(ctx ne null)
      Some(ctx)
    else
      None
  }

  def clear = context.remove()

  def set(ctx: TraceContext) = context.set(ctx)

  def start = set(TraceContext(UUID.randomUUID(), Nil))
}

trait TraceEntry
case class MessageExecutionTime(actorPath: ActorPath, initiated: Long, ended: Long)

case class CodeBlockExecutionTime(blockName: String, begin: Long, end: Long) extends TraceEntry




trait TraceSupport {
  import TraceContext.current


  def trace[T](blockName: String)(f: => T): T = {
    val before = System.currentTimeMillis

    val result = f

    val after = System.currentTimeMillis
    //swapContext(current.get().withEntry(CodeBlockExecutionTime(blockName, before, after)))

    result
  }

  def swapContext(newContext: TraceContext) {
    //current.set(newContext)
  }
}
