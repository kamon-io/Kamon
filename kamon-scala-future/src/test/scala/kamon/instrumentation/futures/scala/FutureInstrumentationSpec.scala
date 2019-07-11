/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.instrumentation.futures.scala

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import kamon.Kamon
import kamon.tag.Lookups.plain
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kamon.testkit.TestSpanReporter
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FutureInstrumentationSpec extends WordSpec with ScalaFutures with Matchers with PatienceConfiguration
    with OptionValues with Eventually with TestSpanReporter {

  import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.{trace, traceAsync}
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  "a Scala Future" when {
    "manually instrumented" should {
      "create Delayed Spans for a traced future and traced callbacks" in {
        Future(trace("future-body")("this is the future body"))
          .map(traceAsync("first-callback")(_.length))
          .map(_ * 10)
          .flatMap(traceAsync("second-callback")(Future(_)))
          .map(_ * 10)
          .filter(traceAsync("third-callback")(_.toString.length > 10))

        val spans = testSpanReporter.spans(200 millis)
        val bodySpan = spans.find(_.operationName == "future-body").get
        val firstCallbackSpan = spans.find(_.operationName == "first-callback").get
        val secondCallbackSpan = spans.find(_.operationName == "second-callback").get
        val thirdCallbackSpan = spans.find(_.operationName == "third-callback").get

        firstCallbackSpan.trace shouldBe bodySpan.trace
        secondCallbackSpan.trace shouldBe bodySpan.trace
        thirdCallbackSpan.trace shouldBe bodySpan.trace

        firstCallbackSpan.parentId shouldBe bodySpan.id
        secondCallbackSpan.parentId shouldBe firstCallbackSpan.id
        thirdCallbackSpan.parentId shouldBe secondCallbackSpan.id

        testSpanReporter.clear()
        ensureExecutionContextIsClean()
      }

      "propagate the last chained Context when failures happen" in {
        Future(trace("future-body")("this is the future body"))
          .map(traceAsync("first-callback")(_.length))
          .map(_ / 0)
          .flatMap(traceAsync("second-callback")(Future(_))) // this will never happen
          .map(_ * 10)
          .recover { case _ => "recovered" }
          .map(traceAsync("third-callback")(_.toString))

        val spans = testSpanReporter.spans(200 millis)
        val bodySpan = spans.find(_.operationName == "future-body").get
        val firstCallbackSpan = spans.find(_.operationName == "first-callback").get
        val thirdCallbackSpan = spans.find(_.operationName == "third-callback").get

        firstCallbackSpan.trace shouldBe bodySpan.trace
        thirdCallbackSpan.trace shouldBe bodySpan.trace
        spans.find(_.operationName == "second-callback") shouldBe empty

        testSpanReporter.clear()
        ensureExecutionContextIsClean()
      }

      "be usable when working with for comprehensions" in {
        for {
          first <- Future(trace("first-future")("this is the future body"))
          second <- Future(trace("second-future")(first.length))
          third <- Future(trace("third-future")(second * 10))
        } yield trace("yield") {
          Kamon.currentSpan().tag("location", "atTheYield")
          "Hello World! " * third
        }

        val spans = testSpanReporter.spans(200 millis)
        val firstFutureSpan = spans.find(_.operationName == "first-future").get
        val secondFutureSpan = spans.find(_.operationName == "second-future").get
        val thirdFutureSpan = spans.find(_.operationName == "third-future").get
        val yieldSpan = spans.find(_.operationName == "yield").get

        firstFutureSpan.trace shouldBe yieldSpan.trace
        secondFutureSpan.trace shouldBe yieldSpan.trace
        thirdFutureSpan.trace shouldBe yieldSpan.trace

        secondFutureSpan.parentId shouldBe firstFutureSpan.id
        thirdFutureSpan.parentId shouldBe secondFutureSpan.id
        yieldSpan.parentId shouldBe thirdFutureSpan.id

        testSpanReporter.clear()
        ensureExecutionContextIsClean()
      }
    }

    "instrumented with the default bytecode instrumentation" should {
      "propagate the context to the thread executing the future's body" in {
        val context = Context.of("key", "value")
        val contextTag = Kamon.storeContext(context) {
          Future(Kamon.currentContext().getTag(plain("key")))
        }

        whenReady(contextTag)(tagValue ⇒ tagValue shouldBe "value")
        ensureExecutionContextIsClean()
      }

      "propagate the context to the thread executing callbacks on the future" in {
        val context = Context.of("key", "value")
        val tagAfterTransformation = Kamon.storeContext(context) {
            Future("Hello Kamon!")
              // The current context is expected to be available during all intermediate processing.
              .map(_.length)
              .flatMap(len ⇒ Future(len.toString))
              .map(_ ⇒ Kamon.currentContext().getTag(plain("key")))
          }

        whenReady(tagAfterTransformation)(tagValue ⇒ tagValue shouldBe "value")
        ensureExecutionContextIsClean()
      }
    }
  }

  def ensureExecutionContextIsClean(): Unit = {
    val ref = new AtomicReference[Option[Context]](None)
    val contextReturn = new ContextReturningRunnable(ref)

    // Ensure that our test Runnable is not being instrumented
    contextReturn.isInstanceOf[HasContext] shouldBe false
    ec.execute(contextReturn)

    val contextInThreadPool = eventually {
      ref.get().value
    }

    contextInThreadPool shouldBe Context.Empty
  }
}

class ContextReturningRunnable(ref: AtomicReference[Option[Context]]) extends Runnable {
  override def run(): Unit = {
    ref.set(Some(Kamon.currentContext()))
  }
}
