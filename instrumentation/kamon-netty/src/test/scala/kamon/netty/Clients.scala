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

import java.net.InetSocketAddress
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter, ChannelInitializer}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil

class NioEventLoopBasedClient(port: Int) {

  private val clientMessagesReceived = new LinkedBlockingQueue[AnyRef]()
  private val group = new NioEventLoopGroup(1)
  private val b = new Bootstrap

  b.group(group)
    .channel(classOf[NioSocketChannel])
    .handler(new HttpClientInitializer(clientMessagesReceived))

  val channel: Channel = b.connect(new InetSocketAddress(port)).sync.channel

  def close(): Unit = {
    channel.close
    group.shutdownGracefully()
  }

  def execute(request: DefaultFullHttpRequest, timeoutMillis: Long = 2000): FullHttpResponse = {
    val future = channel.write(request)
    channel.flush
    future.await(timeoutMillis)
    response()
  }

  def executeWithContent(request: DefaultHttpRequest, content: Seq[HttpContent], timeoutMillis: Long = 2000): FullHttpResponse = {
    val allFutures = (request +: content).map(channel.write)
    channel.flush
    allFutures.foreach(_.await(timeoutMillis))
    response()
  }

  def get(path: String): DefaultFullHttpRequest = {
    val request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
    HttpHeaders.setContentLength(request, 0)
    request
  }

  def postWithChunks(path: String, chunks: String*): (DefaultHttpRequest, Seq[DefaultHttpContent]) = {
    val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path)
    HttpHeaders.setTransferEncodingChunked(request)
    val httpChunks = chunks.map(chunk => new DefaultHttpContent(Unpooled.copiedBuffer(chunk, CharsetUtil.UTF_8)))
    (request, httpChunks :+ new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
  }

  private def response(): FullHttpResponse =
    clientMessagesReceived.poll(2, TimeUnit.SECONDS).asInstanceOf[FullHttpResponse]
}

object NioEventLoopBasedClient {
  def apply(bindAddress: Int): NioEventLoopBasedClient = new NioEventLoopBasedClient(bindAddress)
}

object Clients {
  def withNioClient[A](bindAddress:Int = 9001)(thunk: NioEventLoopBasedClient => A): A = {
    val client = new NioEventLoopBasedClient(bindAddress)
    try thunk(client) finally client.close()
  }
}

private class HttpClientInitializer(received:java.util.Queue[AnyRef]) extends ChannelInitializer[SocketChannel] {
  override def initChannel(ch: SocketChannel): Unit = {
    val p = ch.pipeline
    p.addLast(new HttpClientCodec)
    p.addLast(new HttpObjectAggregator(1024))
    p.addLast(new HttpClientHandler(received))
  }
}

private class HttpClientHandler(received:java.util.Queue[AnyRef]) extends ChannelInboundHandlerAdapter {
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    received.add(msg)
  }
}


