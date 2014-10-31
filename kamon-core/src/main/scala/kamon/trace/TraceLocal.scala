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

package kamon.trace

import scala.collection.concurrent.TrieMap
import kamon.trace.TraceLocal.TraceLocalKey

object TraceLocal {
  trait TraceLocalKey {
    type ValueType
  }

  def store(key: TraceLocalKey)(value: key.ValueType): Unit = TraceRecorder.currentContext match {
    case ctx: DefaultTraceContext ⇒ ctx.traceLocalStorage.store(key)(value)
    case EmptyTraceContext        ⇒ // Can't store in the empty context.
  }

  def retrieve(key: TraceLocalKey): Option[key.ValueType] = TraceRecorder.currentContext match {
    case ctx: DefaultTraceContext ⇒ ctx.traceLocalStorage.retrieve(key)
    case EmptyTraceContext        ⇒ None // Can't retrieve anything from the empty context.
  }
}

class TraceLocalStorage {
  val underlyingStorage = TrieMap[TraceLocal.TraceLocalKey, Any]()

  def store(key: TraceLocalKey)(value: key.ValueType): Unit = underlyingStorage.put(key, value)
  def retrieve(key: TraceLocalKey): Option[key.ValueType] = underlyingStorage.get(key).map(_.asInstanceOf[key.ValueType])
}
