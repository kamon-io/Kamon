package kamon.netty.instrumentation

import kamon.netty.instrumentation.mixin.ChannelContextAwareMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder

class ChannelInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("io.netty.channel.Channel")
    .mixin(classOf[ChannelContextAwareMixin])
}
