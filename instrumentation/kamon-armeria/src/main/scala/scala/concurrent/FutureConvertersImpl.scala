/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package scala.concurrent

import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.util.function.BiConsumer

import scala.concurrent.impl.Promise.DefaultPromise
import scala.util.{Failure, Success, Try}

/**
  * Taken from  https://github.com/scala/scala-java8-compat/blob/master/src/main/scala/scala/concurrent/java8/FutureConvertersImpl.scala
  */
object FutureConvertersImpl {

  class CF[T](val wrapped: Future[T]) extends CompletableFuture[T] with (Try[T] => Unit) {
    override def apply(t: Try[T]): Unit = t match {
      case Success(v) => complete(v)
      case Failure(e) => completeExceptionally(e)
    }
  }

  class P[T](val wrapped: CompletionStage[T]) extends DefaultPromise[T] with BiConsumer[T, Throwable] {
    override def accept(v: T, e: Throwable): Unit = {
      if (e == null) complete(Success(v))
      else complete(Failure(e))
    }
  }

}
