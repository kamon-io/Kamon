package kamon.netty.instrumentation
package advisor

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponse
import kamon.instrumentation.http.HttpMessage
import kamon.netty.instrumentation.mixin.ChannelContextAware

class ServerEncodeMethodAdvisor
object ServerEncodeMethodAdvisor {
  import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodEnter}

  @OnMethodEnter
  def onEnter(@Argument(0) ctx: ChannelHandlerContext,
              @Argument(1) response: AnyRef): Unit = {

    if (response.isHttpResponse()) {
      val handler = ctx.channel().asInstanceOf[ChannelContextAware].getHandler
      handler.buildResponse(toResponse(response.toHttpResponse()),handler.context)
      handler.responseSent()
    }
  }

  private def toResponse(response: HttpResponse): HttpMessage.ResponseBuilder[HttpResponse] = new HttpMessage.ResponseBuilder[HttpResponse] {
    override def build(): HttpResponse =
      response

    override def statusCode: Int =
      response.status().code()

    override def write(header: String, value: String): Unit =
      response.headers().add(header, value)
  }
}
