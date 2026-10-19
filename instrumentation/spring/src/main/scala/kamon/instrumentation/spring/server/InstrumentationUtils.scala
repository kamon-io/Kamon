/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        response.getStatus()
      }

      override def write(header: String, value: String): Unit = {
        // this used to guard against double headers
        // but I believe this is not necessary
        // correct me if I'm wrong!
        _headers += (header -> value)
      }

      override def build(): HttpServletResponse = {
        _headers.foreach(header =>
          response.addHeader(header._1, header._2)
        )
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
        val value = request.getHeader(header)
        // Technically, this could be null
        // so better not risk it
        if (value != null) {
          builder += (header -> value)
        }
      }
      builder.result()
    }
  }

}
