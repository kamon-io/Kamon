package kamon
package instrumentation
package context

import kamon.context.Context
import kanela.agent.api.instrumentation.mixin.Initializer

/**
  * Mixin that exposes access to a Context instance captured by an instrumented instance. The interface exposes means of
  * getting and updating a Context instance, but it does not prescribe any ordering or thread safety guarantees, please
  * refer to the available implementations for more details.
  */
trait HasContext {

  /**
    * Returns the context instance mixed into the instrumented instance.
    */
  def context: Context

  /**
    * Updates the context instance reference mixed into the instrumented instance
    */
  def setContext(context: Context): Unit

}

object HasContext {

  /**
    * HasContext implementation that keeps the Context reference in a mutable field.
    */
  class Mixin(@transient private var _context: Context) extends HasContext {

    override def context: Context =
      if (_context != null) _context else Context.Empty

    override def setContext(context: Context): Unit =
      _context = context
  }

  /**
    * HasContext implementation that keeps the Context reference in a volatile field.
    */
  class VolatileMixin(@transient @volatile private var _context: Context) extends HasContext {

    override def context: Context =
      if (_context != null) _context else Context.Empty

    override def setContext(context: Context): Unit =
      _context = context
  }

  /**
    * HasContext implementation that that keeps the ContextReference in a mutable field and initializes it with the
    * current Context held by Kamon.
    */
  class MixinWithInitializer(@transient private var _context: Context) extends HasContext {

    override def context: Context =
      _context

    override def setContext(context: Context): Unit =
      _context = context

    @Initializer
    def initialize(): Unit =
      setContext(Kamon.currentContext())
  }

  /**
    * HasContext implementation that that keeps the ContextReference in a volatile field and initializes it with the
    * current Context held by Kamon.
    */
  class VolatileMixinWithInitializer(@transient @volatile private var _context: Context) extends HasContext {

    override def context: Context =
      _context

    override def setContext(context: Context): Unit =
      _context = context

    @Initializer
    def initialize(): Unit =
      setContext(Kamon.currentContext())
  }

}
