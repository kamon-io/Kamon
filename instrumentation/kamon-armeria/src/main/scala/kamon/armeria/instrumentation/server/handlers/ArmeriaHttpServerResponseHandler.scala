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

import com.linecorp.armeria.common.HttpStatus.NOT_FOUND
import io.netty.channel.{ChannelHandlerContext, ChannelOutboundHandlerAdapter, ChannelPromise}
import io.netty.handler.codec.http.HttpResponse
import kamon.armeria.instrumentation.server.HasRequestProcessingContext
import kamon.instrumentation.http.{HttpMessage, HttpServerInstrumentation}

final class ArmeriaHttpServerResponseHandler(serverInstrumentation: HttpServerInstrumentation) extends ChannelOutboundHandlerAdapter {

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = {
    if (!msg.isInstanceOf[HttpResponse]) ctx.write(msg, promise)
    else {
      val response = msg.asInstanceOf[HttpResponse]
      val processingContext = ctx.channel().asInstanceOf[HasRequestProcessingContext].getRequestProcessingContext

      /**
        * processingContext.requestHandler.span.operationName() will be empty if an HttpStatusException is thrown
        * see {@link kamon.armeria.instrumentation.server.ServeMethodAdvisor.around()}
        */
      if (response.status().code() == NOT_FOUND.code() && processingContext.requestHandler.span.operationName().isEmpty) {
        processingContext.requestHandler.span.name(serverInstrumentation.settings.unhandledOperationName)
      }

      processingContext.requestHandler.buildResponse(toResponse(response), processingContext.scope.context)

      try ctx.write(msg, promise) finally {
        processingContext.requestHandler.responseSent()
        processingContext.scope.close()
      }
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

object ArmeriaHttpServerResponseHandler {
  def apply(serverInstrumentation: HttpServerInstrumentation): ArmeriaHttpServerResponseHandler =
    new ArmeriaHttpServerResponseHandler(serverInstrumentation)
}

