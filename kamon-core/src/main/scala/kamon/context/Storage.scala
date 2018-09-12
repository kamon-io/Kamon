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

package kamon.context

import kamon.context.Storage.ThreadLocal.ContextHolder

trait Storage {
  def current(): Context
  def store(context: Context): Storage.Scope
}

object Storage {

  trait Scope {
    def context: Context
    def close(): Unit
  }

  /**
    * Wrapper that implements optimized {@link ThreadLocal} access pattern ideal for heavily used
    * ThreadLocals.
    *
    * It is faster to use a mutable holder object and always perform ThreadLocal.get() and never use
    * ThreadLocal.set(), because the value is more likely to be found in the ThreadLocalMap direct hash
    * slot and avoid the slow path ThreadLocalMap.getEntryAfterMiss().
    *
    * Important: this thread local will live in ThreadLocalMap forever, so use with care.
    */
  class ThreadLocal extends Storage {
    private val tls = new java.lang.ThreadLocal[ContextHolder]() {
      override def initialValue() = new ContextHolder(Context.Empty)
    }

    override def current(): Context =
      get()

    override def store(context: Context): Scope = {
      val newContext = context
      val previousContext = get()
      set(newContext)

      new Scope {
        override def context: Context = newContext
        override def close(): Unit = set(previousContext)
      }
    }

    private def get():Context =
      tls.get().value

    private def set(value:Context) : Unit =
      tls.get().value = value
  }

  object ThreadLocal {
    def apply(): ThreadLocal = new ThreadLocal()

    final class ContextHolder(var value:Context)
  }
}