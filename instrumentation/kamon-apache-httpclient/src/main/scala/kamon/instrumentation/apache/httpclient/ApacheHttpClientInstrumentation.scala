package kamon.instrumentation.apache.httpclient

import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers._
import kamon.instrumentation.context.HasContext
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.protocol.HttpContext
import org.apache.http.client.ResponseHandler
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.http.HttpClientInstrumentation.RequestHandler
import kamon.Kamon
import org.apache.http.HttpResponse

class ApacheHttpClientInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("org.apache.http.HttpRequest", "org.apache.http.client.methods.HttpUriRequest")
    .mixin(classOf[HasContext.Mixin])

  onSubTypesOf("org.apache.http.client.HttpClient")
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(1))
        .and(withArgument(0, classOf[HttpUriRequest])),
      classOf[UriRequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(withArgument(0, classOf[HttpUriRequest]))
        .and(withArgument(1, classOf[HttpContext])),
      classOf[UriRequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(withArgument(0, classOf[HttpUriRequest]))
        .and(withArgument(1, classOf[ResponseHandler[_]])),
      classOf[UriRequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(withArgument(0, classOf[HttpUriRequest]))
        .and(withArgument(1, classOf[ResponseHandler[_]]))
        .and(withArgument(2, classOf[HttpContext])),
      classOf[UriRequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(withArgument(0, classOf[HttpHost]))
        .and(withArgument(1, classOf[HttpRequest])),
      classOf[RequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(withArgument(0, classOf[HttpHost]))
        .and(withArgument(1, classOf[HttpRequest]))
        .and(withArgument(2, classOf[HttpContext])),
      classOf[RequestAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(withArgument(0, classOf[HttpHost]))
        .and(withArgument(1, classOf[HttpRequest]))
        .and(withArgument(2, classOf[ResponseHandler[_]])),
      classOf[RequestWithHandlerAdvisor]
    )
    .advise(
      method("execute")
        .and(not(isAbstract()))
        .and(takesArguments(4))
        .and(withArgument(0, classOf[HttpHost]))
        .and(withArgument(1, classOf[HttpRequest]))
        .and(withArgument(2, classOf[HttpContext]))
        .and(withArgument(3, classOf[ResponseHandler[_]])),
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
