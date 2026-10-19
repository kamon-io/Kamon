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

package kamon.instrumentation.executor

import com.google.common.util.concurrent.MoreExecutors
import kamon.Kamon
import kamon.tag.Lookups
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{Callable, CountDownLatch, TimeUnit, Executors => JavaExecutors}
import scala.collection.mutable.ListBuffer

class CaptureContextOnSubmitInstrumentationSpec extends AnyWordSpec with Matchers with ContextTesting with Eventually
    with OptionValues {

  "the CaptureContextOnSubmitInstrumentation" should {
    "capture the context when call execute(Runnable) in DirectExecutor" in {
      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        MoreExecutors.directExecutor().execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call execute(Runnable) in ThreadPool" in {
      val executor = JavaExecutors.newSingleThreadExecutor()
      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ThreadPool" in {
      val executor = JavaExecutors.newSingleThreadExecutor()
      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call execute(Runnable) in ForkJoinPool" in {
      val executor = JavaExecutors.newWorkStealingPool()

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ForkJoinPool" in {
      val executor = JavaExecutors.newWorkStealingPool()

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call execute(Runnable) in ScheduledThreadPool" in {
      val executor = JavaExecutors.newScheduledThreadPool(1)

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ScheduledThreadPool" in {
      val executor = JavaExecutors.newScheduledThreadPool(1)

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call schedule(Runnable,long,TimeUnit) in ScheduledThreadPool" in {
      val executor = JavaExecutors.newScheduledThreadPool(1)

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.schedule(runnable, 1, TimeUnit.MILLISECONDS)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Callable) in ThreadPool" in {
      val executor = JavaExecutors.newSingleThreadExecutor()
      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call submit(Callable) in ScheduledThreadPool" in {
      val executor = JavaExecutors.newSingleThreadExecutor()
      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call schedule(Callable,long,TimeUnit) in ScheduledThreadPool" in {
      val executor = JavaExecutors.newScheduledThreadPool(1)

      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.schedule(callable, 1, TimeUnit.MILLISECONDS)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call submit(Callable) in ForkJoinPool" in {
      val executor = JavaExecutors.newWorkStealingPool()

      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call invokeAll(Colection<Callables>) in ExecutorService" in {
      import scala.collection.JavaConverters._

      val values = Kamon.runWithContext(testContext("all-callables-should-see-this-key")) {
        val callables =
          new CallableWithContext("A") :: new CallableWithContext("B") :: new CallableWithContext("C") :: Nil
        JavaExecutors.newCachedThreadPool().invokeAll(callables.asJava).asScala.foldLeft(ListBuffer.empty[String]) {
          (acc, f) => acc += f.get()
        }
      }
      values should contain allOf ("all-callables-should-see-this-key-A", "all-callables-should-see-this-key-B", "all-callables-should-see-this-key-C")
    }
  }
}

class SimpleRunnable extends Runnable with ContextTesting {
  val latch = new CountDownLatch(1)
  var ctx: Option[String] = _

  override def run(): Unit = {
    ctx = Kamon.currentContext().getTag(Lookups.option(TestKey))
    latch.countDown()
  }
}

class SimpleCallable extends Callable[Option[String]] with ContextTesting {
  val latch = new CountDownLatch(1)
  var ctx: Option[String] = _

  override def call(): Option[String] = {
    ctx = Kamon.currentContext().getTag(Lookups.option(TestKey))
    latch.countDown()
    ctx
  }
}

class CallableWithContext[A](value: String) extends Callable[String] with ContextTesting {
  override def call(): String =
    Kamon.currentContext().getTag(Lookups.option(TestKey)).map(_ + s"-$value").getOrElse("undefined")
}
