/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFutureListener, _}
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.CharsetUtil


class NioEventLoopBasedServer(port: Int) {
  val bossGroup = new NioEventLoopGroup(1)
  val workerGroup = new NioEventLoopGroup
  val b = new ServerBootstrap

  b.group(bossGroup, workerGroup)
    .channel(classOf[NioServerSocketChannel])
    .childHandler(new HttpServerInitializer)

  val channel: Channel = b.bind(port).sync.channel

  def close(): Unit = {
    channel.close
    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
  }
}

class EpollEventLoopBasedServer(port: Int) {
  val bossGroup = new EpollEventLoopGroup(1)
  val workerGroup = new EpollEventLoopGroup
  val b = new ServerBootstrap

  b.group(bossGroup, workerGroup)
    .channel(classOf[EpollServerSocketChannel])
    .childHandler(new HttpServerInitializer)

  val channel: Channel = b.bind(port).sync.channel

  def close(): Unit = {
    channel.close
    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
  }
}

object Servers {
  def withNioServer[A](port:Int = 9001)(thunk: Int => A): A = {
    val server = new NioEventLoopBasedServer(port)
    try thunk(port) finally server.close()
  }

  def withEpollServer[A](port:Int = 9001)(thunk: Int => A): A = {
    val server = new EpollEventLoopBasedServer(port)
    try thunk(port) finally server.close()
  }
}

private class HttpServerInitializer extends ChannelInitializer[SocketChannel] {
  override def initChannel(ch: SocketChannel): Unit = {
    val p = ch.pipeline
    p.addLast(new HttpRequestDecoder(4096, 8192, 8192))
    p.addLast(new HttpResponseEncoder())
    p.addLast(new ChunkedWriteHandler)
    p.addLast(new HttpServerHandler)
  }
}

private class HttpServerHandler extends ChannelInboundHandlerAdapter {
  private val ContentOk = Array[Byte]('H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd')
  private val ContentError = Array[Byte]('E', 'r', 'r', 'o', 'r')

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    if (msg.isInstanceOf[HttpRequest]) {
      val request = msg.asInstanceOf[HttpRequest]

      val isKeepAlive = HttpUtil.isKeepAlive(request)

      if (request.uri().contains("/error")) {
        val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer(ContentError))
        response.headers.set("Content-Type", "text/plain")
        response.headers.set("Content-Length", response.content.readableBytes)
        val channelFuture = ctx.write(response)
        addCloseListener(isKeepAlive)(channelFuture)
      } else if (request.uri().contains("/fetch-in-chunks")) {
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        HttpUtil.setTransferEncodingChunked(response, true)
        response.headers.set("Content-Type", "text/plain")


        ctx.write(response)
          .addListener((cf: ChannelFuture) =>
            writeChunk(cf.channel()).addListener((cf: ChannelFuture) =>
              writeChunk(cf.channel()).addListener((cf: ChannelFuture) =>
                writeChunk(cf.channel()).addListener((cf: ChannelFuture) =>
                  (writeLastContent _).andThen(addCloseListener(isKeepAlive))(cf.channel())))))
      } else {
        val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(ContentOk))
        response.headers.set("Content-Type", "text/plain")
        response.headers.set("Content-Length", response.content.readableBytes)
        val channelFuture = ctx.write(response)
        addCloseListener(isKeepAlive)(channelFuture)
      }

    }

  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush()

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    ctx.close()

  private def writeChunk(channel: Channel, content: ByteBuf = Unpooled.wrappedBuffer(ContentOk)): ChannelFuture = {
    channel.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer("chunkkkkkkkkkkkkk", CharsetUtil.UTF_8)))
  }

  private def writeLastContent(channel: Channel): ChannelFuture = {
    channel.writeAndFlush(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
  }

  private def addCloseListener(isKeepAlive: Boolean)(f: ChannelFuture): Unit = {
    if (!isKeepAlive) f.addListener(ChannelFutureListener.CLOSE)
  }
}
