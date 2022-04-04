package kamon.netty.instrumentation
package advisor

import io.netty.channel.Channel
import kamon.netty.instrumentation.ServerBootstrapInstrumentation.KamonHandler
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit}

class ServerChannelReadMethodAdvisor
object ServerChannelReadMethodAdvisor {

  @OnMethodExit
  def onExit(@Argument(1) child: AnyRef): Unit = {
    val pipeline = child.asInstanceOf[Channel].pipeline()
    if(pipeline.get(KamonHandler) == null)
      pipeline.addFirst(KamonHandler, new KamonHandlerPortable.KamonHandler())
  }
}
