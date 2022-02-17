package kamon.netty.instrumentation.mixin

import kamon.Kamon
import kamon.context.Context
import kanela.agent.api.instrumentation.mixin.Initializer


trait RequestContextAware {
  def setContext(ctx: Context): Unit
  def getContext: Context
}


/**
  * --
  */
class RequestContextAwareMixin extends RequestContextAware {
  @volatile var context: Context = _

  override def setContext(ctx: Context): Unit = context = ctx

  override def getContext: Context = context

  @Initializer
  def init(): Unit = context = Kamon.currentContext()
}
