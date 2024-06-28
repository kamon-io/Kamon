package kamon.instrumentation.apache.cxf.client

import kamon.instrumentation.http.HttpMessage
import org.apache.cxf.message.Message
import org.apache.cxf.message.Message.{HTTP_REQUEST_METHOD, PROTOCOL_HEADERS, RESPONSE_CODE}
import org.slf4j.LoggerFactory

import java.net.{URI, URISyntaxException}
import java.util.Collections.{emptyMap => jEmptyMap, singletonList => jList}
import java.util.{List => JList, Map => JMap}
import scala.collection.JavaConverters._

class ApacheCxfClientHelper

object ApacheCxfClientHelper {

  private val _logger = LoggerFactory.getLogger(classOf[ApacheCxfClientHelper])

  def toRequestBuilder(request: Message): HttpMessage.RequestBuilder[Message] =
    new RequestReader with HttpMessage.RequestBuilder[Message] {

      val delegate: Message = request

      val uri: URI = getUri(request)

      override def write(header: String, value: String): Unit = {
        val builder = Map.newBuilder[String, JList[String]]
        builder ++= getAllHeaders(delegate).map(m => m._1 -> jList(m._2))
        builder += header -> jList(value)
        delegate.put(Message.PROTOCOL_HEADERS, builder.result().asJava)
      }

      override def build(): Message = {
        _logger.trace("Prepared request for instrumentation: {}", this)
        delegate
      }

      override def toString(): String = s"RequestReader(host=$host,port=$port,method=$method,path=$path)"
    }

  def toResponse(message: Message): HttpMessage.Response = new HttpMessage.Response {
    override def statusCode: Int = message.get(RESPONSE_CODE) match {
      case code: Integer => code
      case _ =>
        _logger.debug("Not able to retrieve status code from response")
        -1
    }
  }
  private def getUri(message: Message): URI =
    try {
      getUriAsString(message).map(s => new URI(s)).orNull
    } catch {
      case e: URISyntaxException => throw new RuntimeException(e.getMessage, e)
    }

  private def safeGet(message: Message, key: String): Option[String] =
    if (message.containsKey(key)) {
      message.get(key) match {
        case value: String => Option.apply(value)
        case _             => Option.empty
      }
    } else Option.empty

  private def getUriAsString(message: Message): Option[String] = {
    val requestUrl: Option[String] = safeGet(message, Message.REQUEST_URL).orElse {
      var address = safeGet(message, Message.ENDPOINT_ADDRESS)
      val requestUri = safeGet(message, Message.REQUEST_URI)
      if (requestUri.exists(r => r.startsWith("/"))) {
        if (!address.exists(a => a.startsWith(requestUri.get))) {
          if (address.exists(t => t.endsWith("/") && t.length > 1)) {
            address = address.map(a => a.substring(0, a.length))
          }
          address.map(a => a + requestUri.getOrElse(""))
        } else requestUri
      } else address
    }
    safeGet(message, Message.QUERY_STRING).map(q => requestUrl.map(u => s"$u?$q")).getOrElse(requestUrl)
  }

  private def getAllHeaders(message: Message): Map[String, String] = (message.get(PROTOCOL_HEADERS) match {
    case hs: JMap[String, JList[String]] => hs
    case _                               => jEmptyMap[String, JList[String]]()
  }).asScala.map { case (key, values) => key -> values.asScala.mkString(", ") }.toMap

  private trait RequestReader extends HttpMessage.Request {
    def uri: URI
    def delegate: Message

    override def host: String = if (uri != null) uri.getHost else null

    override def port: Int = if (uri != null) uri.getPort else 0

    override def method: String = delegate.get(HTTP_REQUEST_METHOD).asInstanceOf[String]

    override def path: String = if (uri != null) uri.getPath else null

    override def read(header: String): Option[String] = getAllHeaders(delegate).get(header)

    override def readAll(): Map[String, String] = getAllHeaders(delegate)

    override def url: String = if (uri != null) uri.toString else null
  }
}
