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

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.context.HasContext
import kamon.tag.Lookups.plain
import kamon.testkit.TestSpanReporter
import org.scalatest.OptionValues
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FutureChainingInstrumentationSpec extends AnyWordSpec with Matchers with ScalaFutures with PatienceConfiguration
    with OptionValues with Eventually with TestSpanReporter {

  import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.{traceBody, traceFunc}
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  /**
    *  DEPRECATED
    *
    *  This spec is ignored since Kamon 2.2.0, along with the deprecation of the Future Chaining instrumentation, and
    *  should be completely removed before releasing Kamon 2.3.0.
    *
    *  We are keeping this spec only for the rare case that we might need to fix a bug on the Future Chaining
    *  instrumentation while we keep it in maintenance mode.
    */
  "a Scala Future" ignore {
    "manually instrumented" should {
      "create Delayed Spans for a traced future and traced callbacks" in {
        Future(traceBody("future-body")("this is the future body"))
          .map(traceFunc("first-callback")(_.length))
          .map(_ * 10)
          .flatMap(traceFunc("second-callback")(Future(_)))
          .map(_ * 10)
          .filter(traceFunc("third-callback")(_.toString.length > 10))

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
        Future(traceBody("future-body")("this is the future body"))
          .map(traceFunc("first-callback")(_.length))
          .map(_ / 0)
          .flatMap(traceFunc("second-callback")(Future(_))) // this will never happen
          .map(_ * 10)
          .recover { case _ => "recovered" }
          .map(traceFunc("third-callback")(_.toString))

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
          first <- Future(traceBody("first-future")("this is the future body"))
          second <- Future(traceBody("second-future")(first.length))
          third <- Future(traceBody("third-future")(second * 10))
        } yield traceBody("yield") {
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
        val contextTag = Kamon.runWithContext(context) {
          Future(Kamon.currentContext().getTag(plain("key")))
        }

        whenReady(contextTag)(tagValue => tagValue shouldBe "value")
        ensureExecutionContextIsClean()
      }

      "propagate the context to the thread executing callbacks on the future" in {
        val context = Context.of("key", "value")
        val tagAfterTransformation = Kamon.runWithContext(context) {
            Future("Hello Kamon!")
              // The current context is expected to be available during all intermediate processing.
              .map(_.length)
              .flatMap(len => Future(len.toString))
              .map(_ => Kamon.currentContext().getTag(plain("key")))
          }

        whenReady(tagAfterTransformation)(tagValue => tagValue shouldBe "value")
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
