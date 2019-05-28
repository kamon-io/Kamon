package kamon.instrumentation.akka.http

import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import com.typesafe.config.Config
import kamon.Kamon
import kamon.instrumentation.http.HttpMessage

import scala.collection.immutable

object AkkaHttpInstrumentation {

  @volatile private var _settings: Settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => {
    _settings = readSettings(newConfig)
    HttpExtSingleRequestAdvice.rebuildHttpClientInstrumentation()
  }: Unit)

  /**
    * Returns the current Akka HTTP Instrumentation Settings. These settings are controlled by the configuration found
    * under the "kamon.instrumentation.akka.http" path.
    */
  def settings: Settings =
    _settings


  def toRequest(httpRequest: HttpRequest): HttpMessage.Request = new RequestReader {
    val request = httpRequest
  }

  def toResponse(httpResponse: HttpResponse): HttpMessage.Response = new HttpMessage.Response {
    override val statusCode: Int = httpResponse.status.intValue()
  }

  def toRequestBuilder(httpRequest: HttpRequest): HttpMessage.RequestBuilder[HttpRequest] =
    new RequestReader with HttpMessage.RequestBuilder[HttpRequest] {
      private var _extraHeaders = List.empty[RawHeader]
      val request = httpRequest

      override def write(header: String, value: String): Unit =
        _extraHeaders = RawHeader(header, value) :: _extraHeaders

      override def build(): HttpRequest =
        request.withHeaders(request.headers ++ _extraHeaders)
    }

  def toResponseBuilder(response: HttpResponse): HttpMessage.ResponseBuilder[HttpResponse] = new HttpMessage.ResponseBuilder[HttpResponse] {
    private var _headers: immutable.List[HttpHeader] = Nil

    override def statusCode: Int =
      response.status.intValue()

    override def write(header: String, value: String): Unit =
      _headers = RawHeader(header, value) :: _headers

    override def build(): HttpResponse =
      response.withHeaders(_headers)
  }

  /**
    * Bundles together the read parts of the HTTP Request mapping
    */
  private trait RequestReader extends HttpMessage.Request {
    def request: HttpRequest

    override def url: String =
      request.uri.toString()

    override def path: String =
      request.uri.path.toString()

    override def method: String =
      request.method.value

    override def host: String =
      request.uri.authority.host.address()

    override def port: Int =
      request.uri.authority.port

    override def read(header: String): Option[String] = {
      val headerValue = request.getHeader(header)
      if(headerValue.isPresent)
        Some(headerValue.get().value())
      else None
    }

    override def readAll(): Map[String, String] = {
      val builder = Map.newBuilder[String, String]
      request.headers.foreach(h => builder += (h.name() -> h.value()))
      builder.result()
    }
  }

  case class Settings (
    serverInitialOperationName: String,
    serverUnhandledOperationName: String
  )

  private def readSettings(config: Config): Settings = {
    val akkaHttpConfig = config.getConfig("kamon.instrumentation.akka.http")

    Settings (
      serverInitialOperationName = akkaHttpConfig.getString("server.tracing.initial-operation-name"),
      serverUnhandledOperationName = akkaHttpConfig.getString("server.tracing.unhandled-operation-name")
    )
  }
}
