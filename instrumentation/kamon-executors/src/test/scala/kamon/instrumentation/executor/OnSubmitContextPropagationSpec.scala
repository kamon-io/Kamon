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

import java.util.concurrent.{Callable, ExecutorService, Executors => JavaExecutors}
import com.google.common.util.concurrent.MoreExecutors
import kamon.Kamon
import kamon.instrumentation.executor.ContextAware.{DefaultContextAwareCallable, DefaultContextAwareRunnable}
import kamon.testkit.InitAndStopKamonAfterAll
import kanela.agent.bootstrap.context.ContextHandler
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.util.Random

class OnSubmitContextPropagationSpec extends AnyWordSpec with Matchers with ContextTesting with Eventually
    with OptionValues
    with InitAndStopKamonAfterAll {

  "an instrumented executor with context propagation on submit enabled" should {
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
      val executor = instrument(JavaExecutors.newFixedThreadPool(1))
      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ThreadPool" in {
      val executor = instrument(JavaExecutors.newFixedThreadPool(1))
      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call execute(Runnable) in ForkJoinPool" in {
      val executor = instrument(JavaExecutors.newWorkStealingPool())

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ForkJoinPool" in {
      val executor = instrument(JavaExecutors.newWorkStealingPool())

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call execute(Runnable) in ScheduledThreadPool" in {
      val executor = instrument(JavaExecutors.newScheduledThreadPool(1))

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.execute(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Runnable) in ScheduledThreadPool" in {
      val executor = instrument(JavaExecutors.newScheduledThreadPool(1))

      val ctx = Kamon.runWithContext(testContext("in-runnable-body")) {
        val runnable = new SimpleRunnable
        executor.submit(runnable)
        runnable.latch.await()
        runnable.ctx
      }

      ctx.value should be("in-runnable-body")
    }

    "capture the context when call submit(Callable) in ThreadPool" in {
      val executor = instrument(JavaExecutors.newFixedThreadPool(1))
      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call submit(Callable) in ScheduledThreadPool" in {
      val executor = instrument(JavaExecutors.newScheduledThreadPool(1))
      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call submit(Callable) in ForkJoinPool" in {
      val executor = instrument(JavaExecutors.newWorkStealingPool())

      val ctx = Kamon.runWithContext(testContext("in-callable-body")) {
        val callable = new SimpleCallable
        executor.submit(callable)
        callable.latch.await()
        callable.ctx
      }

      ctx.value should be("in-callable-body")
    }

    "capture the context when call invokeAll(Collection<Callables>) in ExecutorService" in {
      import scala.collection.JavaConverters._

      val values = Kamon.runWithContext(testContext("all-callables-should-see-this-key")) {
        val callables =
          new CallableWithContext("A") :: new CallableWithContext("B") :: new CallableWithContext("C") :: Nil
        instrument(JavaExecutors.newCachedThreadPool()).invokeAll(callables.asJava).asScala.foldLeft(
          ListBuffer.empty[String]
        ) { (acc, f) => acc += f.get() }
      }
      values should contain allOf ("all-callables-should-see-this-key-A", "all-callables-should-see-this-key-B", "all-callables-should-see-this-key-C")
    }

    "wrap Runnable to TestContextAwareRunnable when call ContextHandler.wrapInContextAware" in {
      val simpleRunnable = ContextHandler.wrapInContextAware(new SimpleRunnable)
      simpleRunnable.isInstanceOf[TestContextAwareRunnable] should be(true)
      simpleRunnable.isInstanceOf[DefaultContextAwareRunnable] should be(false)

      val notSimpleRunnable = ContextHandler.wrapInContextAware(new Runnable { override def run(): Unit = {} })
      notSimpleRunnable.isInstanceOf[TestContextAwareRunnable] should be(false)
      notSimpleRunnable.isInstanceOf[DefaultContextAwareRunnable] should be(true)
    }

    "wrap Callable to TestContextAwareCallable when call ContextHandler.wrapInContextAware" in {
      val simpleCallable = ContextHandler.wrapInContextAware(new SimpleCallable)
      simpleCallable.isInstanceOf[TestContextAwareCallable[_]] should be(true)
      simpleCallable.isInstanceOf[DefaultContextAwareCallable[_]] should be(false)

      val notSimpleCallable = ContextHandler.wrapInContextAware(new Callable[String] {
        override def call(): String = "test"
      })
      notSimpleCallable.isInstanceOf[TestContextAwareCallable[_]] should be(false)
      notSimpleCallable.isInstanceOf[DefaultContextAwareCallable[_]] should be(true)
    }
  }

  def instrument(executor: ExecutorService): ExecutorService = {
    ExecutorInstrumentation.instrument(
      executor,
      Random.nextString(10),
      ExecutorInstrumentation.DefaultSettings.propagateContextOnSubmit()
    )
  }
}
