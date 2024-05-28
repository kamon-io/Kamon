package kamon.instrumentation.apache.cxf.client

import org.apache.cxf.Bus
import org.apache.cxf.feature.AbstractFeature
import org.apache.cxf.interceptor.InterceptorProvider

private class TracingClientFeature extends AbstractFeature {

  private val setup = new TracingClientSetupInterceptor()
  private val receive = new TracingClientReceiveInterceptor()
  private val postInvoke = new TracingClientPostInvokeInterceptor()

  override def initializeProvider(provider: InterceptorProvider, bus: Bus): Unit = {
    provider.getOutInterceptors.add(0, setup)
    provider.getOutFaultInterceptors.add(0, setup)
    provider.getInInterceptors.add(0, receive)
    provider.getInInterceptors.add(postInvoke)
    provider.getInFaultInterceptors.add(0, receive)
    provider.getInFaultInterceptors.add(postInvoke)
    super.initializeProvider(provider, bus)
  }
}
