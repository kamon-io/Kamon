package kamon.instrumentation.spring.server

import kamon.instrumentation.http.HttpMessage
import kamon.instrumentation.http.HttpMessage.ResponseBuilder

import java.util.Collections
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object InstrumentationUtils {
  def responseBuilder(response: HttpServletResponse): ResponseBuilder[HttpServletResponse] = {
    new ResponseBuilder[HttpServletResponse] {
      private var _headers = Map.empty[String, String]

      override def statusCode: Int = {
        response.getStatus
      }

      override def write(header: String, value: String): Unit = {
        // guard against double trace-id?
        // double any header really
        if (response.getHeader(header) == null) {
          _headers += (header -> value)
        }
      }

      override def build(): HttpServletResponse = {
        // change response in place
        _headers.foreach(header =>
          response.addHeader(header._1, header._2)
        )
        // and then return it
        response
      }
    }
  }

  def requestReader(incoming: HttpServletRequest): RequestReader = {
    new RequestReader {
      override def request: HttpServletRequest = incoming
    }
  }

  trait RequestReader extends HttpMessage.Request {
    def request: HttpServletRequest

    override def url: String = {
      request.getRequestURL.toString
    }

    override def path: String = {
      request.getServletPath
    }

    override def method: String = {
      request.getMethod
    }

    override def host: String = {
      request.getRemoteHost
    }

    override def port: Int = {
      request.getServerPort
    }

    override def read(header: String): Option[String] = {
      Option(request.getHeader(header)).flatMap(value => {
        if (value.isEmpty) {
          None
        } else {
          Some(value)
        }
      })
    }

    override def readAll(): Map[String, String] = {
      val builder = Map.newBuilder[String, String]
      val headerNames = Option(request.getHeaderNames)
        .getOrElse(Collections.enumeration(Collections.emptyList()))

      // while loop because scala 2.11
      while (headerNames.hasMoreElements) {
        val header = headerNames.nextElement()
        // Technically, this could be null
        // so better not risk it
        Option(request.getHeader(header))
          .foreach(value => builder += (header -> value))
      }

      builder.result()
    }
  }

}
