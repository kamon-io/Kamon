package kamon.instrumentation.finagle.client

import com.twitter.finagle.http.Request
import com.twitter.finagle.http.{Response => FinagleResponse}
import kamon.Kamon
import kamon.instrumentation.http.HttpClientInstrumentation
import kamon.instrumentation.http.HttpMessage

object FinagleHttpInstrumentation {
  type KamonRequestHandler = HttpClientInstrumentation.RequestHandler[Request]

  Kamon.onReconfigure(_ => rebuildHttpClientInstrumentation(): Unit)

  @volatile var httpClientInstrumentation: HttpClientInstrumentation = rebuildHttpClientInstrumentation

  private[finagle] def rebuildHttpClientInstrumentation(): HttpClientInstrumentation = {
    val httpClientConfig = Kamon.config().getConfig("kamon.instrumentation.finagle.http.client")
    httpClientInstrumentation = HttpClientInstrumentation.from(httpClientConfig, "finagle.http.client.request")
    httpClientInstrumentation
  }

  private[finagle] def createHandler(request: Request): KamonRequestHandler = {
    val builder = new RequestBuilder(request)
    val context = Kamon.currentContext()
    httpClientInstrumentation.createHandler(builder, context)
  }

  private[finagle] def processResponse(response: FinagleResponse, handler: KamonRequestHandler): Unit = {
    val builder = new Response(response)
    handler.processResponse(builder)
  }

  final private class RequestBuilder(request: Request) extends HttpMessage.RequestBuilder[Request] {
    override def url: String = request.uri
    override def path: String = request.path
    override def method: String = request.method.name
    override def host: String = request.remoteHost
    override def port: Int = request.remotePort
    override def build(): Request = request
    override def write(header: String, value: String): Unit = request.headerMap.put(header, value)
    override def read(header: String): Option[String] = request.headerMap.get(header)
    override def readAll(): Map[String, String] = request.headerMap.toMap
  }

  final private class Response(response: FinagleResponse) extends HttpMessage.Response {
    override def statusCode: Int = response.statusCode
  }
}
