/*
 * =========================================================================================
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

package kamon.executors
package util

import java.util
import java.util.concurrent.{Callable, ExecutorService, Future, TimeUnit}

import kamon.Kamon
import kamon.util.HasContinuation


class ContinuationAwareExecutorService(underlying: ExecutorService) extends ExecutorService {
  override def isShutdown: Boolean =
    underlying.isShutdown

  override def shutdown(): Unit =
    underlying.shutdown()

  override def shutdownNow(): util.List[Runnable] =
    underlying.shutdownNow()

  override def isTerminated: Boolean =
    underlying.isTerminated

  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
    underlying.awaitTermination(timeout, unit)

  override def submit[A](task: Callable[A]): Future[A] =
    underlying.submit(wrapCallable(task))

  override def submit[A](task: Runnable, result: A): Future[A] =
    underlying.submit(wrapRunnable(task), result)

  override def submit(task: Runnable): Future[_] =
    underlying.submit(wrapRunnable(task))

  override def execute(command: Runnable): Unit =
    underlying.execute(wrapRunnable(command))

  override def invokeAll[A](tasks: util.Collection[_ <: Callable[A]]): util.List[Future[A]] =
    underlying.invokeAll(wrapCallables(tasks))

  override def invokeAll[A](tasks: util.Collection[_ <: Callable[A]], timeout: Long, unit: TimeUnit): util.List[Future[A]] =
    underlying.invokeAll(wrapCallables(tasks), timeout, unit)

  override def invokeAny[A](tasks: util.Collection[_ <: Callable[A]]): A =
    underlying.invokeAny(wrapCallables(tasks))

  override def invokeAny[A](tasks: util.Collection[_ <: Callable[A]], timeout: Long, unit: TimeUnit): A =
    underlying.invokeAny(wrapCallables(tasks), timeout, unit)

  private def wrapRunnable(r: Runnable): ContinuationAwareRunnable = r match {
    case runnable: ContinuationAwareRunnable ⇒ runnable
    case _                                   ⇒ new ContinuationAwareRunnable(r)
  }

  private def wrapCallable[T](r: Callable[T]): ContinuationAwareCallable[T] = r match {
    case callable: ContinuationAwareCallable[T] ⇒ callable
    case _                                      ⇒ new ContinuationAwareCallable[T](r)
  }

  private def wrapCallables[T](tasks: util.Collection[_ <: Callable[T]]) = {
    import scala.collection.JavaConverters._

    tasks.asScala.map(wrapCallable).asInstanceOf[util.Collection[_ <: Callable[T]]]
  }
}

class ContinuationAwareRunnable(r: Runnable) extends Runnable {
  val continuation = Kamon.activeSpan().capture()

  override def run(): Unit = {
    Kamon.withContinuation(continuation) {
      r.run()
    }
  }
}

class ContinuationAwareCallable[A](c: Callable[A]) extends HasContinuation with Callable[A] {
  val continuation = Kamon.activeSpan().capture()

  override def call(): A = {
    Kamon.withContinuation(continuation) {
      c.call()
    }
  }
}

object ContinuationAwareExecutorService {
  def apply(underlying: ExecutorService): ContinuationAwareExecutorService =
    new ContinuationAwareExecutorService(underlying)

  def from(underlying: ExecutorService) =
    new ContinuationAwareExecutorService(underlying)
}