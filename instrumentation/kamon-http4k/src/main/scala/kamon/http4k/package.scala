package kamon

import kamon.instrumentation.http.HttpMessage
import kamon.instrumentation.http.HttpMessage.ResponseBuilder
import org.http4k.core.{Request, Response, Status}

import scala.jdk.CollectionConverters._

package object http4k {

  def buildRequestMessage(request: Request): HttpMessage.Request =
    new HttpMessage.Request {
      override def url: String = request.getUri().toString()

      override def path: String = request.getUri().getPath()

      override def method: String = request.getMethod().name()

      override def host: String = {
        val h = request.getUri().getHost()
        if (h == null) "" else h
      }

      override def port: Int = {
        val p = request.getUri().getPort()
        if (p == null) 0 else p.intValue()
      }

      override def read(header: String): Option[String] =
        Option(request.header(header))

      override def readAll(): Map[String, String] = {
        val builder = Map.newBuilder[String, String]
        request.getHeaders().asScala.foreach { pair =>
          val value = pair.getSecond()
          if (value != null) builder += (pair.getFirst() -> value)
        }
        builder.result()
      }
    }

  def buildResponseBuilder(response: Response): ResponseBuilder[Response] =
    new ResponseBuilder[Response] {
      // http4k Response is immutable. Keep a local var for modifications
      private var _response = response

      override def statusCode: Int = _response.getStatus().getCode()

      override def write(header: String, value: String): Unit =
        _response = _response.header(header, value)

      override def build(): Response = _response
    }

  def errorResponseBuilder: ResponseBuilder[Response] =
    new ResponseBuilder[Response] {
      override def write(header: String, value: String): Unit = ()
      override def statusCode: Int = 500
      override def build(): Response = Response.create(Status.INTERNAL_SERVER_ERROR)
    }

  def notFoundResponseBuilder: ResponseBuilder[Response] =
    new ResponseBuilder[Response] {
      private var _response = Response.create(Status.NOT_FOUND)

      override def statusCode: Int = 404

      override def write(header: String, value: String): Unit =
        _response = _response.header(header, value)

      override def build(): Response = _response
    }
}
