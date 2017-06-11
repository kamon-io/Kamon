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

package kamon
package util

import io.opentracing.ActiveSpan
import io.opentracing.ActiveSpan.Continuation

/**
  *  Utility trait that marks objects carrying an ActiveSpan.Continuation.
  */
trait HasContinuation {
  def continuation: Continuation
}

object HasContinuation {
  private class Default(val continuation: Continuation) extends HasContinuation

  /**
    * Construct a HasContinuation instance by capturing a continuation from the provided active span.
    */
  def from(activeSpan: ActiveSpan): HasContinuation =
    new Default(activeSpan.capture())

  /**
    * Constructs a new HasContinuation instance using Kamon's tracer currently active span.
    */
  def fromTracerActiveSpan(): HasContinuation =
    new Default(Kamon.activeSpan().capture())
}
