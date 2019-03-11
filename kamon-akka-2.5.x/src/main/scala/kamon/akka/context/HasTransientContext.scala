package kamon.akka.context

import kamon.Kamon
import kamon.context.Context

trait ContextContainer  {
  def setContext(context: Context)
  def context: Context
}

object HasTransientContext {

  private class DefaultTransient(@transient var context: Context) extends ContextContainer with Serializable {
    override def setContext(context: Context): Unit = this.context = context
  }

  /**
    * Construct a HasSpan instance that references the provided Context.
    *
    */
  def from(context: Context): ContextContainer =
    new DefaultTransient(context)

  /**
    * Construct a HasContext instance with the current Kamon from Kamon's default context storage.
    *
    */
  def fromCurrentContext(): ContextContainer =
    new DefaultTransient(Kamon.currentContext())

}

