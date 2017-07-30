package kamon.trace

/**
  * A means of storing and retrieving the currently active Span. The application code execution is always considered to
  * contribute to the completion of the operation represented by the currently active Span.
  *
  * The activation of a Span is of temporary nature and users of this API must ensure that all Scopes created via calls
  * to `activate(span)` are timely closed; failing to do so might lead to unexpected behavior. Typically, the same block
  * of code designating a Span as currently active will close the created Scope after finishing execution.
  *
  */
trait ActiveSpanStorage {

  /**
    * @return the currently active Span.
    */
  def activeSpan(): Span

  /**
    * Sets
    * @param span the Span to be set as currently active.
    * @return a [[Scope]] that will finish the designation of the given Span as active once it's closed.
    */
  def activate(span: Span): Scope

}

/**
  * Encapsulates the state (if any) required to handle the removal of a Span from it's currently active designation.
  *
  * Typically a Scope will enclose the previously active Span and return the previously active Span when closed,
  * although no assumptions are made.
  *
  */
trait Scope extends AutoCloseable {

  /**
    * Removes the currently active Span from the ActiveSpanStorage.
    *
    */
  def close(): Unit
}

object ActiveSpanStorage {

  /**
    * A ActiveSpanStorage that uses a [[java.lang.ThreadLocal]] as the underlying storage.
    *
    */
  final class ThreadLocal extends ActiveSpanStorage {
    private val emptySpan = Span.Empty(this)
    private val storage: java.lang.ThreadLocal[Span] = new java.lang.ThreadLocal[Span] {
      override def initialValue(): Span = emptySpan
    }

    override def activeSpan(): Span =
      storage.get()

    override def activate(span: Span): Scope = {
      val previouslyActiveSpan = storage.get()
      storage.set(span)

      new Scope {
        override def close(): Unit = {
          storage.set(previouslyActiveSpan)
        }
      }
    }
  }

  object ThreadLocal {
    def apply(): ThreadLocal = new ThreadLocal()
  }
}