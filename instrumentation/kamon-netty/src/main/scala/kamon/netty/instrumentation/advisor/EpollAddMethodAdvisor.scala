package kamon.netty.instrumentation.advisor

import io.netty.util.concurrent.EventExecutor
import kamon.netty.Metrics
import kamon.netty.util.EventLoopUtils.name
import kanela.agent.libs.net.bytebuddy.asm.Advice.{OnMethodExit, This}

class EpollAddMethodAdvisor
object EpollAddMethodAdvisor {

  @OnMethodExit
  def onExit(@This eventLoop: EventExecutor): Unit = {
    Metrics.forEventLoop(name(eventLoop)).registeredChannels.increment()
  }
}
