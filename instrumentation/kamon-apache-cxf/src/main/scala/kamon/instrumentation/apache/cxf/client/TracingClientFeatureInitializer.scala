package kamon.instrumentation.apache.cxf.client

import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.This
import org.apache.cxf.frontend.ClientProxyFactoryBean

class TracingClientFeatureInitializer
object TracingClientFeatureInitializer {

  @Advice.OnMethodEnter
  def onEnter(@This clientProxyFactoryBean: Any) = clientProxyFactoryBean match {
    case c: ClientProxyFactoryBean => c.getFeatures.add(new TracingClientFeature)
  }
}
