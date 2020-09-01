/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package com.linecorp.armeria.server

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import kamon.Kamon
import kamon.armeria.instrumentation.server.{HasRequestProcessingContext, RequestProcessingContext}
import kamon.instrumentation.http.{HttpMessage, HttpServerInstrumentation}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

final class ArmeriaHttpServerRequestHandler(serverInstrumentation: HttpServerInstrumentation) extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    if (!msg.isInstanceOf[DecodedHttpRequest]) ctx.fireChannelRead(msg)
    else {
      val processingContext = ctx.channel().asInstanceOf[HasRequestProcessingContext]
      val request = msg.asInstanceOf[DecodedHttpRequest]
      val serverRequestHandler = serverInstrumentation.createHandler(toRequest(request, serverInstrumentation.interface(), serverInstrumentation.port()))

      processingContext.setRequestProcessingContext(RequestProcessingContext(serverRequestHandler, Kamon.storeContext(serverRequestHandler.context)))

      ctx.fireChannelRead(msg)
    }
  }

  private def toRequest(request: DecodedHttpRequest, serverHost: String, serverPort: Int): HttpMessage.Request = new HttpMessage.Request {

    override def url: String = request.uri().toString

    override def path: String = request.path()

    override def method: String = request.method().name()

    override def host: String = serverHost

    override def port: Int = serverPort

    override def read(header: String): Option[String] =
      Option(request.headers().get(header))

    override def readAll(): Map[String, String] =
      request.headers().asScala.map(e => e.getKey.toString() -> e.getValue).toMap
  }
}

object ArmeriaHttpServerRequestHandler {
  def apply(serverInstrumentation: HttpServerInstrumentation): ArmeriaHttpServerRequestHandler =
    new ArmeriaHttpServerRequestHandler(serverInstrumentation)
}
