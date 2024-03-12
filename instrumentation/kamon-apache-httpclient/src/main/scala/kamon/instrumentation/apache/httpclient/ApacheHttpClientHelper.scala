package kamon.instrumentation.apache.httpclient

import kamon.instrumentation.http.HttpMessage
import org.slf4j.LoggerFactory
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.client.methods.HttpUriRequest
import java.net.URI
import scala.util.{Try, Failure, Success}
import kamon.instrumentation.context.HasContext
import kamon.Kamon
import org.apache.http.HttpResponse

class ApacheHttpClientHelper
object ApacheHttpClientHelper {

  private val _logger = LoggerFactory.getLogger(classOf[ApacheHttpClientHelper])

  def toRequestBuilder(
    httpHost: HttpHost,
    request: HttpRequest
  ): HttpMessage.RequestBuilder[HttpRequest] =
    new RequestReader with HttpMessage.RequestBuilder[HttpRequest] {
      val delegate = request
      val uri = {
        var parsedUri = getUri(request)
        if (parsedUri != null && httpHost != null) {
          parsedUri = getCompleteUri(httpHost, parsedUri)
        }
        parsedUri
      }

      override def write(header: String, value: String): Unit =
        delegate.addHeader(header, value)

      override def build(): HttpRequest = {
        _logger.trace("Prepared request for instrumentation: {}", this)
        return delegate
      }

      override def toString(): String = s"Host=$host,Port=$port,Method=$method,Path=$path"
    }

  def toRequestBuilder(
    request: HttpUriRequest
  ): HttpMessage.RequestBuilder[HttpUriRequest] =
    new RequestReader with HttpMessage.RequestBuilder[HttpUriRequest] {
      val uri = request.getURI
      val delegate = request

      override def write(header: String, value: String): Unit =
        delegate.addHeader(header, value)

      override def build(): HttpUriRequest = {
        _logger.trace("Prepared request for instrumentation: {}", this)
        return delegate.asInstanceOf[HttpUriRequest]
      }

      override def toString(): String = s"Host=$host,Port=$port,Method=$method,Path=$path"
    }

  def toResponse(response: HttpResponse): HttpMessage.Response = new HttpMessage.Response {
    override def statusCode: Int = {
      if (response == null || response.getStatusLine() == null) {
        _logger.debug("Not able to retrieve status code from response")
        return -1;
      }
      return response.getStatusLine().getStatusCode()
    }
  }

  def getUri(request: HttpRequest): URI =
    Try(new URI(request.getRequestLine.getUri)) match {
      case Failure(exception) =>
        _logger.error("Failed to construct URI from request", exception)
        null
      case Success(value) => value
    }

  def getCompleteUri(host: HttpHost, uri: URI): URI =
    Try(
      new URI(
        host.getSchemeName,
        null,
        host.getHostName,
        host.getPort,
        uri.getPath,
        uri.getQuery,
        uri.getFragment
      )
    ) match {
      case Failure(exception) =>
        _logger.error("Failed to construct URI from request", exception)
        null
      case Success(value) => value
    }

  private trait RequestReader extends HttpMessage.Request {
    def uri: URI
    def delegate: HttpRequest

    override def host: String = {
      if (uri != null) {
        return uri.getHost
      }
      return null
    }

    override def port: Int = {
      if (uri != null) {
        return uri.getPort
      }
      return 0
    }

    override def method: String = delegate.getRequestLine.getMethod

    override def path: String = {
      if (uri != null) {
        return uri.getPath
      }
      return null
    }

    override def read(header: String): Option[String] =
      Some(delegate.getLastHeader(header).getValue)

    override def readAll(): Map[String, String] =
      delegate.getAllHeaders
        .map(header => (header.getName, header.getValue))
        .toMap

    override def url: String = {
      if (uri != null) {
        return uri.toString
      }
      return null
    }

  }
}
