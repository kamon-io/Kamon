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

import java.lang
import java.util.{Map => JavaMap}

import scala.collection.JavaConverters._

class SpanContext(val traceID: Long, val spanID: Long, val parentID: Long, val sampled: Boolean,
  private var baggage: Map[String, String]) extends io.opentracing.SpanContext {

  private[kamon] def addBaggageItem(key: String, value: String): Unit = synchronized {
    baggage = baggage + (key -> value)
  }

  private[kamon] def getBaggage(key: String): String = synchronized {
    baggage.get(key).getOrElse(null)
  }

  private[kamon] def baggageMap: Map[String, String] =
    baggage

  override def baggageItems(): lang.Iterable[JavaMap.Entry[String, String]] = synchronized {
    baggage.asJava.entrySet()
  }
}
