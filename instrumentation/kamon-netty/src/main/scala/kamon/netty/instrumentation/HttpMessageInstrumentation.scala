package kamon.netty.instrumentation

import kamon.netty.instrumentation.mixin.RequestContextAwareMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder

class HttpMessageInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("io.netty.handler.codec.http.HttpMessage")
    .mixin(classOf[RequestContextAwareMixin])
}
