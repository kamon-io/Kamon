package kamon.instrumentation.apache.httpclient

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers._
import kamon.instrumentation.context.HasContext
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.Kamon
import kanela.agent.libs.net.bytebuddy.description.`type`.TypeDescription
import org.apache.http.HttpResponse

class ApacheHttpClientInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("org.apache.http.HttpRequest", "org.apache.http.client.methods.HttpUriRequest")
    .mixin(classOf[HasContext.Mixin])

  onSubTypesOf("org.apache.http.client.HttpClient")
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(1))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.client.methods.HttpUriRequest"))),
      classOf[UriRequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.protocol.HttpContext"))),
      classOf[UriRequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.client.ResponseHandler"))),
      classOf[UriRequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.client.ResponseHandler")))
        .and(takesArgument(2, named[TypeDescription]("org.apache.http.protocol.HttpContext"))),
      classOf[UriRequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.HttpHost")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.HttpRequest"))),
      classOf[RequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.HttpHost")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named[TypeDescription]("org.apache.http.protocol.HttpContext"))),
      classOf[RequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.HttpHost")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named[TypeDescription]("org.apache.http.client.ResponseHandler"))),
      classOf[RequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(4))
        .and(takesArgument(0, named[TypeDescription]("org.apache.http.HttpHost")))
        .and(takesArgument(1, named[TypeDescription]("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named[TypeDescription]("org.apache.http.protocol.HttpContext")))
        .and(takesArgument(3, named[TypeDescription]("org.apache.http.client.ResponseHandler"))),
      classOf[RequestWithHandlerAdvisor]
    )

}

object ApacheHttpClientInstrumentation {

  Kamon.onReconfigure(_ =>
    ApacheHttpClientInstrumentation.rebuildHttpClientInstrumentation(): Unit
  )

  @volatile var httpClientInstrumentation: HttpClientInstrumentation =
    rebuildHttpClientInstrumentation()

  private[httpclient] def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig =
      Kamon.config().getConfig("kamon.instrumentation.apache.httpclient")
    httpClientInstrumentation =
      HttpClientInstrumentation.from(httpClientConfig, "apache.httpclient")
    return httpClientInstrumentation
  }

  def processResponse(handler: RequestHandler[_], response: HttpResponse, t: Throwable): Unit = {
    if (t != null) {
      handler.span.fail(t)
    } else {
      handler.processResponse(ApacheHttpClientHelper.toResponse(response))
    }
  }

}
