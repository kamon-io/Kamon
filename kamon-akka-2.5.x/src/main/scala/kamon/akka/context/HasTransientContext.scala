package kamon.akka.context

import kamon.Kamon
import kamon.context.{Context, HasContext}

object HasTransientContext {

  private case class DefaultTransient(@transient context: Context) extends HasContext

  /**
    * Construct a HasSpan instance that references the provided Context.
    *
    */
  def from(context: Context): HasContext =
    DefaultTransient(context)

  /**
    * Construct a HasContext instance with the current Kamon from Kamon's default context storage.
    *
    */
  def fromCurrentContext(): HasContext =
    DefaultTransient(Kamon.currentContext())

}

