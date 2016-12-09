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

import java.util.function.Supplier

import kamon.trace.TraceLocal.TraceLocalKey

import scala.collection.concurrent.TrieMap

object TraceLocal {

  trait TraceLocalKey[T]

  trait AvailableToMdc extends TraceLocalKey[String] {
    def mdcKey: String
  }

  object AvailableToMdc {
    case class DefaultKeyAvailableToMdc(mdcKey: String) extends AvailableToMdc

    def fromKey(mdcKey: String): AvailableToMdc = DefaultKeyAvailableToMdc(mdcKey)
    def apply(mdcKey: String): AvailableToMdc = fromKey(mdcKey)
  }

  def store[T](key: TraceLocalKey[T])(value: Any): Unit = Tracer.currentContext match {
    case ctx: MetricsOnlyContext ⇒ ctx.traceLocalStorage.store(key)(value)
    case EmptyTraceContext       ⇒ // Can't store in the empty context.
  }

  def retrieve[T](key: TraceLocalKey[T]): Option[T] = Tracer.currentContext match {
    case ctx: MetricsOnlyContext ⇒ ctx.traceLocalStorage.retrieve(key)
    case EmptyTraceContext       ⇒ None // Can't retrieve anything from the empty context.
  }

  // Java variant
  @throws(classOf[NoSuchElementException])
  def get[T](key: TraceLocalKey[T]): T = retrieve(key).get

  def getOrElse[T](key: TraceLocalKey[T], code: Supplier[T]): T = retrieve(key).getOrElse(code.get)

  def storeForMdc(key: String, value: String): Unit = store(AvailableToMdc.fromKey(key))(value)

  def newTraceLocalKey[T]: TraceLocalKey[T] = new TraceLocalKey[T] {}
}

class TraceLocalStorage {
  val underlyingStorage = TrieMap[TraceLocalKey[_], Any]()

  def store[T](key: TraceLocalKey[T])(value: Any): Unit = underlyingStorage.put(key, value)
  def retrieve[T](key: TraceLocalKey[T]): Option[T] = underlyingStorage.get(key).map(_.asInstanceOf[T])
}
