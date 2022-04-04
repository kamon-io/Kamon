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


import io.netty.handler.codec.http.{HttpRequest, HttpResponse}
import kamon.context.Context
import kamon.netty.instrumentation.mixin.{ChannelContextAware, RequestContextAware}

package object instrumentation {

  implicit class ChannelSyntax(val channel: io.netty.channel.Channel) extends AnyVal {
    def toContextAware(): ChannelContextAware =
      channel.asInstanceOf[ChannelContextAware]

    def getContext(): Context =
      channel.toContextAware().getContext
  }

  implicit class RequestSyntax(val request: HttpRequest) extends AnyVal {
    def toContextAware(): RequestContextAware =
      request.asInstanceOf[RequestContextAware]

    def getContext(): Context =
      request.toContextAware().getContext
  }

  implicit class HttpSyntax(val obj: AnyRef) extends AnyVal {
    def toHttpRequest(): HttpRequest =
      obj.asInstanceOf[HttpRequest]

    def isHttpRequest(): Boolean =
      obj.isInstanceOf[HttpRequest]

    def toHttpResponse(): HttpResponse =
      obj.asInstanceOf[HttpResponse]

    def isHttpResponse(): Boolean =
      obj.isInstanceOf[HttpResponse]
  }

  def isError(statusCode: Int): Boolean =
    statusCode >= 500 && statusCode < 600
}
