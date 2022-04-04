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

package kamon.netty.instrumentation

import kamon.netty.instrumentation.advisor.{ServerChannelReadMethodAdvisor, ServerGroupMethodAdvisor}
import kamon.netty.instrumentation.mixin.EventLoopMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ServerBootstrapInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("io.netty.channel.EventLoopGroup")
    .mixin(classOf[EventLoopMixin])

  onType("io.netty.bootstrap.ServerBootstrap")
    .advise(method("group").and(takesArguments(2)), classOf[ServerGroupMethodAdvisor])

  onType("io.netty.bootstrap.ServerBootstrap$ServerBootstrapAcceptor")
    .advise(method("channelRead").and(takesArguments(2)), classOf[ServerChannelReadMethodAdvisor])

//  @Before("execution(* io.netty.bootstrap.ServerBootstrap.group(..)) && args(bossGroup, workerGroup)")
//  def onNewServerBootstrap(bossGroup:NamedEventLoopGroup, workerGroup:NamedEventLoopGroup):Unit = {
//    if(bossGroup == workerGroup) {
//      bossGroup.name = BossGroupName
//      workerGroup.name = BossGroupName
//    } else {
//      bossGroup.name = BossGroupName
//      workerGroup.name = WorkerGroupName
//    }
//  }

//  @After("execution(* io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor.channelRead(..)) && args(ctx, child)")
//  def onChannelRead(ctx: ChannelHandlerContext, child: Channel):Unit = {
//    val pipeline = child.pipeline()
//    if(pipeline.get(KamonHandler) == null)
//      pipeline.addFirst(KamonHandler, new KamonHandler())
//  }
}

object ServerBootstrapInstrumentation {
  val BossGroupName = "boss-group"
  val WorkerGroupName = "worker-group"
  val KamonHandler = "kamon-handler"
}

//@Aspect
//class EventLoopMixin {
//  @DeclareMixin("io.netty.channel.EventLoopGroup+")
//  def mixinEventLoopGroupWithNamedEventLoopGroup: NamedEventLoopGroup = new NamedEventLoopGroup {}
//}
//
//trait NamedEventLoopGroup {
//  var name:String = _
//}