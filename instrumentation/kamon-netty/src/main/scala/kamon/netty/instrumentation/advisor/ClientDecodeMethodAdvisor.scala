package kamon.netty.instrumentation
package advisor

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponse
import kamon.instrumentation.http.HttpMessage
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit, Thrown}

class ClientDecodeMethodAdvisor
object ClientDecodeMethodAdvisor {

  @OnMethodExit(onThrowable = classOf[Throwable], inline = false)
  def onExit(@Argument(0) _ctx: AnyRef,
             @Argument(2) out: java.util.List[AnyRef],
             @Thrown failure: Throwable): Unit = {

    val ctx = _ctx.asInstanceOf[ChannelHandlerContext]

    if (failure != null) {
      val handler = ctx.channel().toContextAware().getClientHandler
      handler.span.fail(failure.getMessage, failure).finish()
      throw failure
    } else if (out.size() > 0 && out.get(0).isHttpResponse()) {
      val response = out.get(0).asInstanceOf[HttpResponse]
      val handler = ctx.channel().toContextAware().getClientHandler
      handler.processResponse(new HttpMessage.Response {
        override def statusCode: Int = response.status().code()
      })
    }
  }
}
