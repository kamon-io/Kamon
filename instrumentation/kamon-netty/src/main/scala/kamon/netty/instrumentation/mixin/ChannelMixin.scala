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

package kamon.netty.instrumentation.mixin

import java.time.Instant

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kanela.agent.api.instrumentation.mixin.Initializer


trait ChannelContextAware {
  def setClientHandler(handler: HttpClientInstrumentation.RequestHandler[_]): Unit
  def getClientHandler: HttpClientInstrumentation.RequestHandler[_]
  def setStartTime(value: Instant): Unit
  def getStartTime: Instant
  def setContext(ctx: Context):Unit
  def getContext: Context
  def setHandler(handler:RequestHandler): Unit
  def getHandler:RequestHandler
}

/**
  * --
  */
class ChannelContextAwareMixin extends ChannelContextAware {
  @volatile var startTime: Instant = _
  @volatile var context:Context = _
  @volatile var serverHandler:RequestHandler = _
  @volatile var clientHandler:HttpClientInstrumentation.RequestHandler[_] = _

  override def setStartTime(value: Instant): Unit = startTime = value

  override def getStartTime: Instant = startTime

  override def setContext(ctx: Context): Unit = context = ctx

  override def getContext: Context = context

  override def setHandler(handler: RequestHandler): Unit = serverHandler = handler

  override def getHandler: RequestHandler = serverHandler

  override def setClientHandler(handler: HttpClientInstrumentation.RequestHandler[_]): Unit = clientHandler = handler

  override def getClientHandler: HttpClientInstrumentation.RequestHandler[_] = clientHandler

  @Initializer
  def init(): Unit = context = Kamon.currentContext()

}
