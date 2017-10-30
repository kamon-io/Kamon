/*
 * =========================================================================================
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

package kamon.play

import kamon.Kamon
import kamon.trace.Span
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialAction, EssentialFilter, RequestHeader, Result}

class KamonFilter extends EssentialFilter {

  override def apply(next: EssentialAction): EssentialAction = new EssentialAction {
    override def apply(requestHeader: RequestHeader): Iteratee[Array[Byte], Result] = {
      Kamon.currentContext().get(Span.ContextKey).setOperationName(Play.generateOperationName(requestHeader))
      next.apply(requestHeader)
    }
  }
}