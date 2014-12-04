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

import kamon.trace.TraceLocal.TraceLocalKey

import scala.collection.concurrent.TrieMap

object TraceLocal {

  trait TraceLocalKey {
    type ValueType
  }

  trait AvailableToMdc extends TraceLocalKey {
    override type ValueType = String
    def mdcKey: String
  }

  object AvailableToMdc {
    case class DefaultKeyAvailableToMdc(mdcKey: String) extends AvailableToMdc

    def fromKey(mdcKey: String): AvailableToMdc = DefaultKeyAvailableToMdc(mdcKey)
    def apply(mdcKey: String): AvailableToMdc = fromKey(mdcKey)
  }

  case class HttpContext(agent: String, uri: String, xforwarded: String)

  object HttpContextKey extends TraceLocal.TraceLocalKey { type ValueType = HttpContext }

  def store(key: TraceLocalKey)(value: key.ValueType): Unit = TraceRecorder.currentContext match {
    case ctx: MetricsOnlyContext ⇒ ctx.traceLocalStorage.store(key)(value)
    case EmptyTraceContext       ⇒ // Can't store in the empty context.
  }

  def retrieve(key: TraceLocalKey): Option[key.ValueType] = TraceRecorder.currentContext match {
    case ctx: MetricsOnlyContext ⇒ ctx.traceLocalStorage.retrieve(key)
    case EmptyTraceContext       ⇒ None // Can't retrieve anything from the empty context.
  }

  def storeForMdc(key: String, value: String): Unit = store(AvailableToMdc.fromKey(key))(value)
}

class TraceLocalStorage {
  val underlyingStorage = TrieMap[TraceLocal.TraceLocalKey, Any]()

  def store(key: TraceLocalKey)(value: key.ValueType): Unit = underlyingStorage.put(key, value)
  def retrieve(key: TraceLocalKey): Option[key.ValueType] = underlyingStorage.get(key).map(_.asInstanceOf[key.ValueType])
}
