package kamon.netty.instrumentation.advisor

import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.EventExecutor
import kamon.netty.Metrics
import kamon.netty.util.EventLoopUtils.name
import kanela.agent.libs.net.bytebuddy.asm.Advice.{OnMethodExit, Return, This}

class RegisterMethodAdvisor
object RegisterMethodAdvisor {

  @OnMethodExit
  def onExit(@This eventLoop: EventExecutor, @Return _channelFuture: AnyRef): Unit = {
    val channelFuture = _channelFuture.asInstanceOf[ChannelFuture]
    val registeredChannels = Metrics.forEventLoop(name(eventLoop)).registeredChannels

    if (channelFuture.isSuccess) registeredChannels.increment()
    else channelFuture.addListener((future: ChannelFuture) => {
      if(future.isSuccess) registeredChannels.increment()
    })
  }
}
