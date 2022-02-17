package kamon.netty.instrumentation

import java.net.URI

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpRequest
import kamon.Kamon
import kamon.instrumentation.http.HttpMessage
import kamon.netty.Netty

object HttpRequestContext {

  def withContext(request: HttpRequest, ctx: ChannelHandlerContext): HttpRequest = {
    val currentContext = request.getContext()
    val handler = Netty.clientInstrumentation.createHandler(toRequestBuilder(request), currentContext)

    ctx.channel().toContextAware().setClientHandler(handler)
    request
  }


  private def toRequestBuilder(request: HttpRequest): HttpMessage.RequestBuilder[HttpRequest] =
    new HttpMessage.RequestBuilder[HttpRequest] {

      val uri = new URI(request.uri())

      import scala.collection.JavaConverters._

      private var _newHttpHeaders: List[(String, String)] = List.empty

      override def write(header: String, value: String): Unit =
        _newHttpHeaders = (header -> value) :: _newHttpHeaders

      override def build(): HttpRequest = {
        _newHttpHeaders.foreach{ case(key, value) => request.headers().add(key, value)}
        request
      }

      override def read(header: String): Option[String] =
        Option(request.headers().get(header))

      override def readAll(): Map[String, String] =
        request.headers.asScala.map(m => (m.getKey, m.getValue)).toMap

      override def url: String =
        uri.toURL.toString

      override def path: String =
        uri.getPath

      override def method: String =
        request.method().name()

      override def host: String =
        uri.getHost

      override def port: Int =
        uri.getPort
    }

}

object KamonHandlerPortable {

  @ChannelHandler.Sharable
  class KamonHandler extends ChannelInboundHandlerAdapter {
    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
      ctx.channel().toContextAware().setStartTime(Kamon.clock().instant())
      super.channelRead(ctx, msg)
    }
  }
}
