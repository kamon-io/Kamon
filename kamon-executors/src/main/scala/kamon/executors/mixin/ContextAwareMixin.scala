package kamon.executors.mixin

import kamon.Kamon
import kamon.context.Context
import kanela.agent.api.instrumentation.mixin.Initializer

import scala.beans.BeanProperty

trait ContextAware {
  def getContext:Context
}

class ContextAwareMixin extends ContextAware {
  @BeanProperty var context: Context = _

  @Initializer
  def initialize(): Unit = this.context =
    Kamon.currentContext()
}