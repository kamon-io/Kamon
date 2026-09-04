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

package kamon

import kamon.context.{Context, Storage}
import kamon.trace.Span

import scala.util.control.NonFatal

/**
  * Exposes in-process Context storage APIs. See the ContextStorage companion object for more info on the default
  * storage implementation.
  */
trait ContextStorage {
  import ContextStorage._

  /**
    * Returns the current Context on Kamon's Context Storage. As the default behavior, this will return Context.Empty if
    * no other Context has been stored on the calling thread.
    */
  def currentContext(): Context =
    _contextStorage.current()

  /**
    * Returns the Span held by the current Context, if any. As the default behavior, this will return Span.Empty if the
    * current Context does not contain a Span.
    */
  def currentSpan(): Span =
    _contextStorage.current().get(Span.Key)

  /**
    * Stores the provided Context on Kamon's Context Storage and returns a Scope that removes that Context from the it
    * upon closing. When a Scope is closed, it will always set the current Context to the Context that was available
    * right before it was created.
    *
    * NOTE: The default implementation of Scope is not thread safe and, unless there is a good reason not to, users
    *       should always close scopes before leaving a thread, otherwise there is a risk of leaving "dirty" threads
    *       that could cause unexpected correlation between Contexts from different operations. It is strongly
    *       recommended to use any of the .storeContext(...) variants which ensure closing Scopes after finishing
    *       execution.
    *
    */
  def storeContext(context: Context): Storage.Scope =
    _contextStorage.store(context)

  /**
    * Temporarily stores the provided Context on Kamon's Context Storage. The provided Context will be stored before
    * executing the provided function and removed right after it finishes executing.
    */
  @inline def runWithContext[T](context: Context)(f: => T): T = {
    val scope = _contextStorage.store(context)
    try {
      f
    } finally {
      scope.close()
    }
  }

  /**
    * Temporarily stores the provided Context Key on Kamon's Context Storage. The provided Context key will be added to
    * the current Context and stored before executing the provided function, then removed right after execution
    * finishes.
    */
  def runWithContextEntry[T, K](key: Context.Key[K], value: K)(f: => T): T =
    runWithContext(currentContext().withEntry(key, value))(f)

  /**
    * Temporarily stores the provided Context tag on Kamon's Context Storage. The provided Context tag will be added to
    * the current Context and stored before executing the provided function, then removed right after execution
    * finishes.
    */
  def runWithContextTag[T](key: String, value: String)(f: => T): T =
    runWithContext(currentContext().withTag(key, value))(f)

  /**
    * Temporarily stores the provided Context tag on Kamon's Context Storage. The provided Context tag will be added to
    * the current Context and stored before executing the provided function, then removed right after execution
    * finishes.
    */
  def runWithContextTag[T](key: String, value: Boolean)(f: => T): T =
    runWithContext(currentContext().withTag(key, value))(f)

  /**
    * Temporarily stores the provided Context tag on Kamon's Context Storage. The provided Context tag will be added to
    * the current Context and stored before executing the provided function, then removed right after execution
    * finishes.
    */
  def runWithContextTag[T](key: String, value: Long)(f: => T): T =
    runWithContext(currentContext().withTag(key, value))(f)

  /**
    * Temporarily stores the provided Span on Kamon's Context Storage. The provided Span will be added to the current
    * Context and stored before executing the provided function, then removed right after execution finishes.
    */
  def runWithSpan[T](span: Span)(f: => T): T =
    runWithSpan(span, true)(f)

  /**
    * Temporarily stores the provided Span on Kamon's Context Storage. The provided Span will be added to the current
    * Context and stored before executing the provided function, then removed right after execution finishes.
    * Optionally, this function can finish the provided Span once the function execution finishes.
    */
  @inline def runWithSpan[T](span: Span, finishSpan: Boolean)(f: => T): T = {
    try {
      runWithContextEntry(Span.Key, span)(f)
    } catch {
      case NonFatal(t) =>
        span.fail(t.getMessage, t)
        throw t

    } finally {
      if (finishSpan)
        span.finish()
    }
  }
}

object ContextStorage {

  /**
    * Kamon's global Context Storage instance. Unless stated otherwise, all instrumentation provided with Kamon ends
    * up using this Storage instance for in-process Context propagation. This default storage is based on a ThreadLocal
    * wrapper which stores the "current" Context for each thread on the application. Instrumentation will typically
    * store and remove Context instances for small periods of time as events flow through the system and the
    * instrumentation follows them around.
    */
  private val _contextStorage: Storage = {
    val storageTypeStr = Option(sys.props("kamon.context.storageType"))

    if (sys.props("kamon.context.debug") == "true")
      Storage.Debug()
    else {
      storageTypeStr match {
        case None                    => Storage.CrossThreadLocal()
        case Some("debug")           => Storage.Debug()
        case Some("sameThreadScope") => Storage.ThreadLocal()
        case Some("default")         => Storage.CrossThreadLocal()
        case Some(other) => throw new IllegalArgumentException(s"Unrecognized kamon.context.storageType value: $other")
      }
    }
  }
}
