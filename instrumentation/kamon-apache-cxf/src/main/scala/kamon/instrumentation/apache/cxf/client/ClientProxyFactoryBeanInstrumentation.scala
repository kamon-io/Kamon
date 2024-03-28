package kamon.instrumentation.apache.cxf.client

import kamon.Kamon
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kanela.agent.api.instrumentation.InstrumentationBuilder
import org.apache.cxf.message.Message

class ClientProxyFactoryBeanInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("org.apache.cxf.frontend.ClientProxyFactoryBean")
    .advise(method("initFeatures"), classOf[TracingClientFeatureInitializer])
}

object ClientProxyFactoryBeanInstrumentation {
  Kamon.onReconfigure(_ =>
    ClientProxyFactoryBeanInstrumentation.rebuildHttpClientInstrumentation(): Unit
  )

  @volatile var cxfClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation()

  private def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.apache.cxf")
    cxfClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "apache.cxf.client")
    cxfClientInstrumentation
  }

  def processResponse(handler: RequestHandler[_], message: Message, t: Throwable = null): Unit = {
    if (t != null) handler.span.fail(t).finish()
    else handler.processResponse(ApacheCxfClientHelper.toResponse(message))
  }
}
