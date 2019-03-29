package kamon

import kamon.context.{Context, Storage}
import kamon.trace.Span

import scala.util.control.NonFatal

trait ContextStorage {
  private val _contextStorage = Storage.ThreadLocal()

  def currentContext(): Context =
    _contextStorage.current()

  def currentSpan(): Span =
    _contextStorage.current().get(Span.Key)

  def storeContext(context: Context): Storage.Scope =
    _contextStorage.store(context)

  def withContext[T](context: Context)(f: => T): T = {
    val scope = _contextStorage.store(context)
    try {
      f
    } finally {
      scope.close()
    }
  }

  def withContextKey[T, K](key: Context.Key[K], value: K)(f: => T): T =
    withContext(currentContext().withKey(key, value))(f)

  def withSpan[T](span: Span)(f: => T): T =
    withSpan(span, true)(f)

  def withSpan[T](span: Span, finishSpan: Boolean)(f: => T): T = {
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
