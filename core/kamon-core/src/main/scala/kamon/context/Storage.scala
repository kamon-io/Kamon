/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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
package context

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReferenceArray

/**
  * A temporary space to store a Context instance.
  */
trait Storage {

  /**
    * Returns the Context instance held in the Storage, or Context.Empty if nothing is stored.
    */
  def current(): Context

  /**
    * Temporarily puts a Context instance in the Storage.
    */
  def store(context: Context): Storage.Scope

}

object Storage {

  /**
    * Encapsulates the extend during which a Context is held by an Storage implementation. Once a Scope is closed, the
    * Context will be removed from the Storage that created the Scope.
    */
  trait Scope extends AutoCloseable {

    /**
      * Returns the Context managed by this Scope.
      */
    def context: Context

    /**
      * Removes the Context from the Storage. Implementations will typically have a reference to the Context that was
      * present before the Scope was created and put it back in the Storage upon closing.
      */
    def close(): Unit
  }

  /**
    * Wrapper that implements an optimized ThreadLocal access pattern ideal for heavily used ThreadLocals. It is faster
    * to use a mutable holder object and always perform ThreadLocal.get() and never use ThreadLocal.set(), because the
    * value is more likely to be found in the ThreadLocalMap direct hash slot and avoid the slow path of
    * ThreadLocalMap.getEntryAfterMiss().
    *
    * Credit to @trask from the FastThreadLocal in glowroot. One small change is that we don't use an kamon-defined
    * holder object as that would prevent class unloading.
    */
  class ThreadLocal extends Storage {
    private val tls = new java.lang.ThreadLocal[Array[AnyRef]]() {
      override def initialValue(): Array[AnyRef] =
        Array(Context.Empty)
    }

    override def current(): Context =
      tls.get()(0).asInstanceOf[Context]

    override def store(newContext: Context): Scope = {
      val ref = tls.get()
      val previousContext = ref(0)
      ref(0) = newContext

      new Scope {
        override def context: Context = newContext
        override def close(): Unit = ref(0) = previousContext
      }
    }
  }

  object ThreadLocal {

    def apply(): Storage.ThreadLocal =
      new Storage.ThreadLocal()
  }


  /**
    * A Storage implementation that keeps track of all Contexts across all Threads in the application and exposes them
    * through its companion object. Using the Debug storage can only be enabled when the System Property
    * "kamon.context.debug" is set to "true" (we don't allow this be discovered from configuration because it can cause
    * initialization issues when Kamon is first initialized via instrumentation trying to access the current Context).
    *
    * This implementation is considerably less efficient than the default implementation since it is taking at least two
    * different stack traces for every store/close operation pair. Do not use this for any reason other than debugging
    * Context propagation issues (like, dirty Threads) in a controlled environment.
    *
    */
  class Debug extends Storage {
    import Debug._

    private val _tls = new java.lang.ThreadLocal[AtomicReferenceArray[AnyRef]]() {
      override def initialValue(): AtomicReferenceArray[AnyRef] = {
        val localArray = new AtomicReferenceArray[AnyRef](3)
        localArray.set(0, Context.Empty)
        localArray.set(1, Thread.currentThread())
        localArray.set(2, stackTraceString())
        _allThreadContexts.add(localArray)
        localArray
      }
    }

    override def current(): Context =
      _tls.get().get(0).asInstanceOf[Context]

    override def store(newContext: Context): Scope = {
      val ref = _tls.get()
      val previousContext = ref.get(0)
      ref.set(0, newContext)
      ref.set(2, stackTraceString())

      new Scope {
        override def context: Context =
          newContext

        override def close(): Unit = {
          ref.set(0, previousContext)
          ref.set(2, stackTraceString())
        }
      }
    }

    private def stackTraceString(): String =
      Thread.currentThread().getStackTrace().mkString("\n")
  }

  object Debug {

    private val _allThreadContexts = new ConcurrentLinkedQueue[AtomicReferenceArray[AnyRef]]()

    def apply(): Storage.Debug =
      new Storage.Debug()

    /**
      * Contains information about the current context in a thread. The lastUpdateStackTracer can be either a store or
      * a scope close, depending on what was the last action executed on the thread.
      */
    case class ThreadContext (
      thread: Thread,
      currentContext: Context,
      lastUpdateStackTrace: String
    )

    /**
      * Returns all Threads where a Context has been stored, along with the current Context on that thread and the
      * stack trace from when it was last modified. Users will typically take this information log it periodically for
      * debugging purposes.
      */
    def allThreadContexts(): Seq[ThreadContext] = {
      val contexts = Seq.newBuilder[ThreadContext]
      val threads = _allThreadContexts.iterator()

      while(threads.hasNext) {
        val threadEntry = threads.next()
        contexts += ThreadContext(
          thread = threadEntry.get(1).asInstanceOf[Thread],
          currentContext = threadEntry.get(0).asInstanceOf[Context],
          lastUpdateStackTrace = threadEntry.get(2).asInstanceOf[String]
        )
      }

      contexts.result()
    }
  }
}
