/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.trace.logging

import kamon.trace.TraceLocal.AvailableToMdc
import kamon.trace.{ EmptyTraceContext, MetricsOnlyContext, TraceContext, TraceRecorder }

import org.slf4j.MDC

trait MdcKeysSupport {

  def withMdc[A](thunk: ⇒ A): A = {
    val keys = copyToMdc(TraceRecorder.currentContext)
    try thunk finally keys.foreach(key ⇒ MDC.remove(key))
  }

  private[this] def copyToMdc(traceContext: TraceContext): Iterable[String] = traceContext match {
    case ctx: MetricsOnlyContext ⇒
      ctx.traceLocalStorage.underlyingStorage.collect {
        case (available: AvailableToMdc, value) ⇒ Map(available.mdcKey -> String.valueOf(value))
      }.map { value ⇒ value.map { case (k, v) ⇒ MDC.put(k, v); k } }.flatten

    case EmptyTraceContext ⇒ Iterable.empty[String]
  }
}
