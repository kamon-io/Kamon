package kamon

import kamon.context.{Context, Storage}
import kamon.trace.Span

import scala.util.control.NonFatal

/**
  * Exposes in-process Context storage APIs. Kamon uses a Thread-local storage for keeping track of the current Context
  * in any given Thread and this class exposes the necessary means to inspect that context and to temporarily change the
  * current context on a Thread.
  */
trait ContextStorage {
  private val _contextStorage = Storage.ThreadLocal()

  /**
    * Returns the current Context on the calling Thread. As the default behavior this will return Context.Empty if no
    * other Context instance has been made current on the calling Thread.
    */
  def currentContext(): Context =
    _contextStorage.current()


  /**
    * Returns the Span held by the current Context, if any. As the default behavior, this will return Span.Empty if the
    * current Context does not contain a Span.
    */
  def currentSpan(): Span =
    _contextStorage.current().get(Span.Key)


  /**
    * Sets the provided Context as current and returns a Scope that removes the context from the current placeholder
    * upon closing. When a Scope is closed, it will always set the current Context to the Context instance that was
    * available right before it was created.
    */
  def storeContext(context: Context): Storage.Scope =
    _contextStorage.store(context)


  /**
    * Creates a new Context using the current Context and the provided Context key, and sets it as the current Context
    * while the provided function runs.
    */
  def withContextKey[T, K](key: Context.Key[K], value: K)(f: => T): T =
    withContext(currentContext().withKey(key, value))(f)


  /**
    * Creates a new Context using the current Context and the provided Context tag, and sets it as the current Context
    * while the provided function runs.
    */
  def withContextTag[T](key: String, value: String)(f: => T): T =
    withContext(currentContext().withTag(key, value))(f)


  /**
    * Creates a new Context using the current Context and the provided Context tag, and sets it as the current Context
    * while the provided function runs.
    */
  def withContextTag[T](key: String, value: Boolean)(f: => T): T =
    withContext(currentContext().withTag(key, value))(f)


  /**
    * Creates a new Context using the current Context and the provided Context tag, and sets it as the current Context
    * while the provided function runs.
    */
  def withContextTag[T](key: String, value: Long)(f: => T): T =
    withContext(currentContext().withTag(key, value))(f)


  /**
    * Creates a new Context using the current Context and the provided Span, and sets it as the current Context while
    * the provided function runs. Additionally, this function will finish the provided Span once the function execution
    * finishes.
    */
  def withSpan[T](span: Span)(f: => T): T =
    withSpan(span, true)(f)


  /**
    * Sets the provided Context as current while the provided function runs.
    */
  @inline def withContext[T](context: Context)(f: => T): T = {
    val scope = _contextStorage.store(context)
    try {
      f
    } finally {
      scope.close()
    }
  }


  /**
    * Creates a new Context using the current Context and the provided Span, and sets it as the current Context while
    * the provided function runs. Optionally, this function can finish the provided Span once the function execution
    * finishes.
    */
  @inline def withSpan[T](span: Span, finishSpan: Boolean)(f: => T): T = {
    try {
      withContextKey(Span.Key, span)(f)
    } catch {
      case NonFatal(t) =>
        span.fail(t.getMessage, t)
        throw t

    } finally {
      if(finishSpan)
        span.finish()
    }
  }

}
