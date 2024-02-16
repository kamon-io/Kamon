package kamon

import org.http4s.{Header, Headers, Request, Response, Status}
import kamon.instrumentation.http.HttpMessage
import kamon.instrumentation.http.HttpMessage.ResponseBuilder
import org.typelevel.ci.CIString

package object http4s {

  def buildRequestMessage[F[_]](inner: Request[F]): HttpMessage.Request =
    new HttpMessage.Request {
      override def url: String = inner.uri.toString()

      override def path: String = inner.uri.path.renderString

      override def method: String = inner.method.name

      override def host: String =
        inner.uri.authority.map(_.host.value).getOrElse("")

      override def port: Int = inner.uri.authority.flatMap(_.port).getOrElse(0)

      override def read(header: String): Option[String] =
        inner.headers.get(CIString(header)).map(_.head.value)

      override def readAll(): Map[String, String] = {
        val builder = Map.newBuilder[String, String]
        inner.headers.foreach(h => builder += (h.name.toString -> h.value))
        builder.result()
      }
    }

  def errorResponseBuilder[F[_]]: HttpMessage.ResponseBuilder[Response[F]] =
    new ResponseBuilder[Response[F]] {
      override def write(header: String, value: String): Unit = ()
      override def statusCode: Int = 500
      override def build(): Response[F] =
        Response[F](status = Status.InternalServerError)
    }

  // TODO both of these
  def notFoundResponseBuilder[F[_]]: HttpMessage.ResponseBuilder[Response[F]] =
    new ResponseBuilder[Response[F]] {
      private var _headers = Headers.empty

      override def write(header: String, value: String): Unit =
        _headers = _headers.put(Header.Raw(CIString(header), value))

      override def statusCode: Int = 404
      override def build(): Response[F] =
        Response[F](status = Status.NotFound, headers = _headers)
    }

  def getResponseBuilder[F[_]](
      response: Response[F]
  ): ResponseBuilder[Response[F]] =
    new HttpMessage.ResponseBuilder[Response[F]] {
      private var _headers = response.headers

      override def statusCode: Int = response.status.code

      override def build(): Response[F] = response.withHeaders(_headers)

      override def write(header: String, value: String): Unit =
        _headers = _headers.put(Header.Raw(CIString(header), value))
    }

  def getRequestBuilder[F[_]](
      request: Request[F]
  ): HttpMessage.RequestBuilder[Request[F]] =
    new HttpMessage.RequestBuilder[Request[F]] {
      private var _headers = request.headers

      override def build(): Request[F] = request.withHeaders(_headers)

      override def write(header: String, value: String): Unit =
        _headers = _headers.put(Header.Raw(CIString(header), value))

      override def url: String = request.uri.toString()

      override def path: String = request.uri.path.renderString

      override def method: String = request.method.name

      override def host: String =
        request.uri.authority.map(_.host.value).getOrElse("")

      override def port: Int =
        request.uri.authority.flatMap(_.port).getOrElse(0)

      override def read(header: String): Option[String] =
        _headers.get(CIString(header)).map(_.head.value)

      override def readAll(): Map[String, String] = {
        val builder = Map.newBuilder[String, String]
        request.headers.foreach(h => builder += (h.name.toString -> h.value))
        builder.result()
      }
    }

}
