package kamon.jdbc.instrumentation.mixin

import kanela.agent.api.instrumentation.mixin.Initializer

trait ProcessOnlyOnce {
  def processOnlyOnce[T](thunk: => T): Option[T]
  def finish(): Unit
}

class ProcessOnlyOnceMixin extends ProcessOnlyOnce {

  @volatile private var elementInProgress: Option[Any] = _

  override def processOnlyOnce[T](thunk: => T): Option[T] = {
    if (elementInProgress.isEmpty) {
      val e = Some(thunk)
      elementInProgress = e
      e
    } else None
  }

  def finish(): Unit = elementInProgress = None

  @Initializer
  def init(): Unit = elementInProgress = None

}
