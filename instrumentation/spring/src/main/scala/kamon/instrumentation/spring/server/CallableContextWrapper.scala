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

package kamon.instrumentation.spring.server

import kamon.Kamon

import java.util.concurrent.Callable

object CallableContextWrapper {

  def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
    private val _context = Kamon.currentContext()

    override def call(): T = {
      val scope = Kamon.storeContext(_context)
      try { callable.call() }
      finally {
        scope.close()
      }
    }
  }
}
