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

package kamon.instrumentation.pekko.http

import org.apache.pekko.http.scaladsl.server.Directive0
import org.apache.pekko.http.scaladsl.server.directives.BasicDirectives
import kamon.Kamon


trait TracingDirectives extends BasicDirectives {

  /**
    * Assigns a new operation name to the Span representing the processing of the current request and ensures that a
    * Sampling Decision is taken in case none has been taken so far.
    */
  def operationName(name: String, takeSamplingDecision: Boolean = true): Directive0 = mapRequest { req =>
    val operationSpan = Kamon.currentSpan()
    operationSpan.name(name)

    if(takeSamplingDecision)
      operationSpan.takeSamplingDecision()

    req
  }
}

object TracingDirectives extends TracingDirectives
