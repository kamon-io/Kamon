package kamon.netty.instrumentation.advisor

import kamon.netty.instrumentation.ServerBootstrapInstrumentation.{BossGroupName, WorkerGroupName}
import kamon.netty.instrumentation.mixin.NamedEventLoopGroup
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodEnter}

class ServerGroupMethodAdvisor
object ServerGroupMethodAdvisor {

  @OnMethodEnter
  def onEnter(@Argument(value = 0) _bossGroup: AnyRef,
              @Argument(value = 1) _workerGroup: AnyRef): Unit = {
    val bossGroup = _bossGroup.asInstanceOf[NamedEventLoopGroup]
    val workerGroup = _workerGroup.asInstanceOf[NamedEventLoopGroup]
    if(bossGroup == workerGroup) {
      bossGroup.setName(BossGroupName)
      workerGroup.setName(BossGroupName)
    } else {
      bossGroup.setName(BossGroupName)
      workerGroup.setName(WorkerGroupName)
    }
  }
}
