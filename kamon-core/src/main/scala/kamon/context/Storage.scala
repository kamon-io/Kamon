/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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
    * <p> It is faster to use a mutable holder object and always perform ThreadLocal.get() and never use
    * ThreadLocal.set(), because the value is more likely to be found in the ThreadLocalMap direct hash
    * slot and avoid the slow path ThreadLocalMap.getEntryAfterMiss().
    *
    * <p> Credit to @trask from the FastThreadLocal in glowroot.
    *
    * <p> One small change is that we don't use an kamon-defined holder object as that would prevent class unloading.
    *
    * */
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
    def apply(): ThreadLocal = new ThreadLocal()
  }
}
