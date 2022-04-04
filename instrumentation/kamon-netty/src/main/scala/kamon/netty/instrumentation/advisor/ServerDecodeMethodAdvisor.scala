package kamon.netty.instrumentation
package advisor

import java.net.URI

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import kamon.instrumentation.http.HttpMessage
import kamon.netty.Netty
import kamon.netty.instrumentation.mixin.ChannelContextAware
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodExit}

class ServerDecodeMethodAdvisor
object ServerDecodeMethodAdvisor {

  @OnMethodExit
  def onExit(@Argument(0) ctx: AnyRef,
             @Argument(2) out: java.util.List[AnyRef]): Unit = {

    if (out.size() > 0 && out.get(0).isHttpRequest()) {
      val request = out.get(0).toHttpRequest()
      val channel = ctx.asInstanceOf[ChannelHandlerContext].channel().asInstanceOf[ChannelContextAware]
      val serverRequestHandler = Netty.serverInstrumentation.createHandler(toRequest(request))
      channel.setHandler(serverRequestHandler)
    }
  }

  private def toRequest(request: HttpRequest): HttpMessage.Request = new HttpMessage.Request {
    import scala.collection.JavaConverters._

    val uri = new URI(request.uri())

    override def url: String = uri.toURL.toString
    override def path: String = uri.getPath
    override def method: String = request.method().name()
    override def host: String = uri.getHost
    override def port: Int = uri.getPort

    override def read(header: String): Option[String] =
      Option(request.headers().get(header))

    override def readAll(): Map[String, String] =
      request.headers().entries().asScala.map(e => e.getKey -> e.getValue).toMap
  }
}
