package kamon.instrumentation.akka.http

import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import kamon.Kamon
import kamon.instrumentation.http.HttpMessage

import scala.collection.immutable

object AkkaHttpInstrumentation {

  Kamon.onReconfigure(_ => AkkaHttpClientInstrumentation.rebuildHttpClientInstrumentation(): Unit)

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
    private var _headers = response.headers

    override def statusCode: Int =
      response.status.intValue()

    override def write(header: String, value: String): Unit =
      _headers = RawHeader(header, value) +: _headers

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
}
