/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.trace

import kamon.context.{Context}
import kamon.trace.Tracer.SpanBuilder

/**
  * Allows users to customize and add additional information to Spans created by instrumentation. The typical use
  * case for SpanCustomizer instances is to provide proper operation names in situations where the instrumentation
  * is unable to generate a reasonable enough operation name, e.g. JDBC and HTTP Client calls, instead of having a
  * default operation name using the statement type or target host a SpanCustomizer can be provided to assign operation
  * names like "queryUsers" or "getUserProfile" instead.
  *
  * Instrumentation wanting to take advantage of SpanCustomizers should look for an instance in the current context
  * using SpanCustomizer.ContextKey.
  *
  */
trait SpanCustomizer {
  def customize(spanBuilder: SpanBuilder): SpanBuilder
}

object SpanCustomizer {

  val Noop = new SpanCustomizer {
    override def customize(spanBuilder: SpanBuilder): SpanBuilder = spanBuilder
  }

  val ContextKey = Context.key[SpanCustomizer]("span-customizer", Noop)

  def forOperationName(operationName: String): SpanCustomizer = new SpanCustomizer {
    override def customize(spanBuilder: SpanBuilder): SpanBuilder =
      spanBuilder.withOperationName(operationName)
  }
}


