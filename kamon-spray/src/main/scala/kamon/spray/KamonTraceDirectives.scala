/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.spray

import spray.routing.directives.BasicDirectives
import spray.routing._
import kamon.trace.Tracer

trait KamonTraceDirectives extends BasicDirectives {
  def traceName(name: String, tags: Map[String, String] = Map.empty): Directive0 = mapRequest { req ⇒
    tags.foreach { case (key, value) ⇒ Tracer.currentContext.addTag(key, value) }
    Tracer.currentContext.rename(name)
    req
  }
}

object KamonTraceDirectives extends KamonTraceDirectives
