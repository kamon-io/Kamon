package kamon.trace


trait Scope extends AutoCloseable {
  def close(): Unit
}

trait ActiveSpanSource {
  def activeSpan(): Span

  def activate(span: Span): Scope
  def activate(span: Span, finishOnClose: Boolean): Scope
}

object ActiveSpanSource {

  final class ThreadLocalBased extends ActiveSpanSource {
    private val emptySpan = Span.Empty(this)
    private val storage: ThreadLocal[Span] = new ThreadLocal[Span] {
      override def initialValue(): Span = emptySpan
    }

    override def activeSpan(): Span =
      storage.get()

    override def activate(span: Span): Scope =
      activate(span, finishOnClose = false)

    override def activate(span: Span, finishOnClose: Boolean): Scope = {
      val previouslyActiveSpan = storage.get()
      storage.set(span)

      new Scope {
        override def close(): Unit = {
          storage.set(previouslyActiveSpan)
          if (finishOnClose && span != null)
            span.finish()
        }
      }
    }
  }

  object ThreadLocalBased {
    def apply(): ThreadLocalBased = new ThreadLocalBased()
  }
}