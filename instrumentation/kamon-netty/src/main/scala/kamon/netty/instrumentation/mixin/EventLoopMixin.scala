package kamon.netty.instrumentation.mixin

trait NamedEventLoopGroup {
  def setName(name: String): Unit
  def getName: String
}

class EventLoopMixin extends NamedEventLoopGroup {

  @volatile var _name: String = _

  override def setName(name: String): Unit = _name = name

  override def getName: String = _name
}
