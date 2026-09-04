package kamon.instrumentation.apache.cxf.client

import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.This
import org.apache.cxf.frontend.ClientProxyFactoryBean

import scala.annotation.static

class TracingClientFeatureInitializer
object TracingClientFeatureInitializer {

  @Advice.OnMethodEnter
  @static def onEnter(@This clientProxyFactoryBean: Any) = clientProxyFactoryBean match {
    case c: ClientProxyFactoryBean => c.getFeatures.add(new TracingClientFeature)
  }
}
