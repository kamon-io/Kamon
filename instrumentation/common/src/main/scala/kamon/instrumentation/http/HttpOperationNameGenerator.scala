/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.http

import kamon.instrumentation.http.HttpMessage.Request

/**
  * Generates an operation name based on information available on an HTTP request message. Implementations of this
  * class might be used to generate client and/or server side operation names.
  */
trait HttpOperationNameGenerator {

  /**
    * Returns the name to be assigned to the HTTP operation, or None if a name cannot be determined.
    */
  def name(request: Request): Option[String]

}

object HttpOperationNameGenerator {

  /**
    * Uses the request Host to assign a name.
    */
  object Hostname extends HttpOperationNameGenerator {
    override def name(request: Request): Option[String] = {
      Option(request.host).filter(_.nonEmpty) orElse request.read("host").map(_.takeWhile(_ != ':'))
    }
  }

  /**
    * Uses the request Host and Port to assign a name.
    */
  object HostnameAndPort extends HttpOperationNameGenerator {
    override def name(request: Request): Option[String] =
      Option(request.host).map(h => s"$h:${request.port}")
  }

  /**
    * Uses the request HTTP Method to assign a name.
    */
  object Method extends HttpOperationNameGenerator {
    override def name(request: Request): Option[String] =
      Option(request.method)
  }

  /**
    * Uses a static name.
    */
  class Static(name: String) extends HttpOperationNameGenerator {
    override def name(request: Request): Option[String] =
      Option(name)
  }

}
