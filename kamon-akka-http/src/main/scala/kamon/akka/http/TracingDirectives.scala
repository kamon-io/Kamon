/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package kamon.akka.http

import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.Directive0
import kamon.Kamon


trait TracingDirectives extends BasicDirectives {

  def operationName(name: String, tags: Map[String, String] = Map.empty): Directive0 = mapRequest { req ⇒
    val operationSpan = Kamon.currentSpan()
    operationSpan.setOperationName(name)
    tags.foreach { case (key, value) ⇒ operationSpan.tag(key, value) }
    req
  }
}

object KamonTraceDirectives extends TracingDirectives
