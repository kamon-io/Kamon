package kamon

import java.util.UUID
import akka.actor.ActorPath


case class TraceContext(id: UUID, entries: List[TraceEntry]) {
  def fork = this.copy(entries = Nil)
  def withEntry(entry: TraceEntry) = this.copy(entries = entry :: entries)
}

object TraceContext {
  private val context = new ThreadLocal[Option[TraceContext]] {
    override def initialValue(): Option[TraceContext] = None
  }

  def current = context.get

  def clear = context.remove

  def set(ctx: TraceContext) = context.set(Some(ctx))

  def start = set(TraceContext(UUID.randomUUID(), Nil))
}

trait TraceEntry
case class MessageExecutionTime(actorPath: ActorPath, initiated: Long, ended: Long)

case class CodeBlockExecutionTime(blockName: String, begin: Long, end: Long) extends TraceEntry




trait TraceSupport {
  def withContext[Out](func: => Any => Out, ctx: TraceContext) = {
    TraceContext.set(ctx)
    val result = func
    TraceContext.clear

    result
  }
}
