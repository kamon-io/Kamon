package kamon.instrumentation

import kamon.Kamon
import kamon.context.Context

/**
  * Common mixins used across multiple instrumentation modules.
  */
object Mixin {

  /**
    * Utility trait that marks objects carrying a reference to a Context instance.
    *
    */
  trait HasContext {
    def context: Context
  }

  object HasContext {
    private case class Default(context: Context) extends HasContext

    /**
      * Construct a HasSpan instance that references the provided Context.
      *
      */
    def from(context: Context): HasContext =
      Default(context)

    /**
      * Construct a HasContext instance with the current Kamon from Kamon's default context storage.
      *
      */
    def fromCurrentContext(): HasContext =
      Default(Kamon.currentContext())
  }
}
