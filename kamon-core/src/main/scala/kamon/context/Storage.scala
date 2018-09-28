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


  class ThreadLocal extends Storage {
    private val tls = new java.lang.ThreadLocal[Context]() {
      override def initialValue(): Context = Context.Empty
    }

    override def current(): Context =
      tls.get()

    override def store(context: Context): Scope = {
      val newContext = context
      val previousContext = tls.get()
      tls.set(newContext)

      new Scope {
        override def context: Context = newContext
        override def close(): Unit = tls.set(previousContext)
      }
    }
  }

  object ThreadLocal {
    def apply(): ThreadLocal = new ThreadLocal()
  }
}