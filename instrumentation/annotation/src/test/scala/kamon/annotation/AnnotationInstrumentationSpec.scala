/*
 * =========================================================================================
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

package kamon.annotation

import java.util.concurrent.{CompletableFuture, CompletionStage}
import kamon.annotation.api._
import kamon.metric.{Histogram => _, RangeSampler => _, Timer => _}
import kamon.module.Module.Registration
import kamon.tag.Lookups._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection, Reconfigure, SpanInspection}
import kamon.trace.Span
import kamon.{Kamon, testkit}
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AnnotationInstrumentationSpec extends AnyWordSpec
    with Matchers
    with Eventually
    with SpanSugar
    with Reconfigure
    with InstrumentInspection.Syntax
    with SpanInspection
    with MetricInspection.Syntax
    with InitAndStopKamonAfterAll
    with OptionValues {

  "the Kamon Annotation module" should {

    "create a new Span for methods annotated with @Trace" in {
      for (id <- 1 to 10) Annotated(id).trace()

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.kind shouldBe Span.Kind.Internal
        span.operationName shouldBe "trace"
        spanTags("slow-service") shouldBe "service"
        spanTags("env") shouldBe "prod"
      }
    }

    "create a new Span for methods annotated with @Trace without parameters" in {
      for (id <- 1 to 10) Annotated(id).traceWithoutParameters()

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        span.kind shouldBe Span.Kind.Internal
        span.operationName shouldBe "kamon.annotation.Annotated.traceWithoutParameters"
      }
    }

    "create a new Span for methods annotated with @Trace without parameters and returning a Future" in {
      for (id <- 1 to 10) Annotated(id).traceWithFuture()

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        span.kind shouldBe Span.Kind.Internal
        span.operationName shouldBe "kamon.annotation.Annotated.traceWithoutParameters"
      }
    }

    "create a new Span for methods annotated with @Trace without parameters and returning a CompletionStage" in {
      for (id <- 1 to 10) Annotated(id).traceWithCompletionStage()

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        span.kind shouldBe Span.Kind.Internal
        span.operationName shouldBe "kamon.annotation.Annotated.traceWithCompletionStage"
      }
    }

    "pickup a CustomizeInnerSpan from the current context and apply it to the new spans" in {
      for (id <- 1 to 10) Annotated(id).traceWithSpanCustomizer()

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "customized-operation-name"
        spanTags("slow-service") shouldBe "service"
        spanTags("env") shouldBe "prod"
      }
    }

    "count the invocations of a method annotated with @Count" in {
      for (id <- 1 to 10) Annotated(id).count()

      Kamon.counter("count").withoutTags().value() should be(10)
    }

    "count the invocations of a method annotated with @Count without parameters" in {
      for (id <- 1 to 10) Annotated(id).countWithoutParameters()

      Kamon.counter("kamon.annotation.Annotated.countWithoutParameters").withoutTags().value() should be(10)
    }

    "count the invocations of a method annotated with @Count and evaluate EL expressions" in {
      for (id <- 1 to 2) Annotated(id).countWithEL()

      Kamon.counter("counter:1").withTags(TagSet.from(Map("counter" -> "1", "env" -> "prod"))).value() should be(1)
      Kamon.counter("counter:2").withTags(TagSet.from(Map("counter" -> "1", "env" -> "prod"))).value() should be(1)
    }

    "count the current invocations of a method annotated with @TrackConcurrency" in {
      for (id <- 1 to 10) {
        Annotated(id).trackConcurrency()
      }
      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax").withoutTags().distribution().max should be(0)
      }
    }

    "count the current invocations of a method annotated with @TrackConcurrency without parameters" in {
      100 times {
        Future {
          Annotated(10).trackConcurrencyWithoutParameters()
        }
      }

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("kamon.annotation.Annotated.trackConcurrencyWithoutParameters")
          .withoutTags().distribution().max should be > 0L
      }
    }

    "count the current invocations of a method annotated with @TrackConcurrency with Futures" in {
      100 times {
        Future {
          Annotated(10).trackConcurrencyWithFuture()
        }
      }

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("kamon.annotation.Annotated.trackConcurrencyWithFuture")
          .withoutTags().distribution().max should be > 0L
      }
    }

    "count the current invocations of a method annotated with @TrackConcurrency with CompletionStage" in {
      100 times {
        Future {
          Annotated(10).trackConcurrencyWithCompletionStage()
        }
      }

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("kamon.annotation.Annotated.trackConcurrencyWithCompletionStage")
          .withoutTags().distribution().max should be > 0L
      }
    }

    "count the current invocations of a method annotated with @TrackConcurrency and evaluate EL expressions" in {
      for (id <- 1 to 10) Annotated(id).trackConcurrencyWithEL()

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax:1").withTags(
          TagSet.from(Map("minMax" -> "1", "env" -> "dev"))
        ).distribution().sum should be(0)
        Kamon.rangeSampler("minMax:2").withTags(
          TagSet.from(Map("minMax" -> "1", "env" -> "dev"))
        ).distribution().sum should be(0)
      }
    }

    "measure the time spent in the execution of a method annotated with @Time" in {
      for (id <- 1 to 1) Annotated(id).time()

      Kamon.timer("time").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time without parameters" in {
      for (id <- 1 to 1) Annotated(id).timeWithoutParameters()

      Kamon.timer("kamon.annotation.Annotated.timeWithoutParameters").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and returning a Future" in {
      Annotated(1).timeWithFuture()
      Kamon.timer("kamon.annotation.Annotated.timeWithFuture").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and returning a CompletionStage" in {
      Annotated(1).timeWithCompletionStage()
      Kamon.timer("kamon.annotation.Annotated.timeWithCompletionStage").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and evaluate EL expressions" in {
      for (id <- 1 to 1) Annotated(id).timeWithEL()

      Kamon.timer("time:1").withTags(
        TagSet.from(Map("slow-service" -> "service", "env" -> "prod"))
      ).distribution().count should be(1)
    }

    "record the operationName returned by a method annotated with @Histogram" in {
      for (operationName <- 1 to 5) Annotated().histogram(operationName)

      val snapshot = Kamon.histogram("histogram").withoutTags().distribution()
      snapshot.count should be(5)
      snapshot.min should be(1)
      snapshot.max should be(5)
      snapshot.sum should be(15)
    }

    "record the operationName returned by a method annotated with @Histogram and evaluate EL expressions" in {
      for (operationName <- 1 to 2) Annotated(operationName).histogramWithEL(operationName)

      val snapshot1 =
        Kamon.histogram("histogram:1").withTags(TagSet.from(Map("histogram" -> "hdr", "env" -> "prod"))).distribution()
      snapshot1.count should be(1)
      snapshot1.min should be(1)
      snapshot1.max should be(1)
      snapshot1.sum should be(1)

      val snapshot2 =
        Kamon.histogram("histogram:2").withTags(TagSet.from(Map("histogram" -> "hdr", "env" -> "prod"))).distribution()
      snapshot2.count should be(1)
      snapshot2.min should be(2)
      snapshot2.max should be(2)
      snapshot2.sum should be(2)
    }
  }

  @volatile var registration: Registration = _
  val reporter = new testkit.TestSpanReporter.BufferingSpanReporter()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("test-module", reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
    super.afterAll()
  }

  def stringTag(span: Span.Finished)(tag: String): String = {
    span.tags.get(plain(tag))
  }
}

case class Annotated(id: Long) {

  @Trace(operationName = "trace", tags = "${'slow-service':'service', 'env':'prod'}")
  def trace(): Unit = {}

  @Trace
  def traceWithoutParameters(): Unit = {}

  @Trace
  def traceWithFuture(): Future[String] =
    Future.successful("Hello")

  @Trace
  def traceWithCompletionStage(): CompletionStage[String] =
    CompletableFuture.completedFuture("Hello")

  @CustomizeInnerSpan(operationName = "customized-operation-name")
  def traceWithSpanCustomizer(): Unit = {
    val spanBuilder = Kamon.spanBuilder("unknown").tag("slow-service", "service").tag("env", "prod").start()

    Kamon.runWithSpan(spanBuilder) {
      customizeSpan()
    }
  }

  @Count()
  def countWithoutParameters(): Unit = {}

  @Count(name = "count")
  def count(): Unit = {}

  @Count(name = "${'counter:' += this.id}", tags = "${'counter':'1', 'env':'prod'}")
  def countWithEL(): Unit = {}

  @TrackConcurrency(name = "minMax")
  def trackConcurrency(): Unit = {}

  @TrackConcurrency
  def trackConcurrencyWithoutParameters(): Unit = {}

  @TrackConcurrency
  def trackConcurrencyWithFuture(): Future[String] =
    Future.successful("Hello")

  @TrackConcurrency
  def trackConcurrencyWithCompletionStage(): CompletionStage[String] =
    CompletableFuture.completedFuture("Hello")

  @TrackConcurrency(name = "#{'minMax:' += this.id}", tags = "#{'minMax':'1', 'env':'dev'}")
  def trackConcurrencyWithEL(): Unit = {}

  @Time(name = "time")
  def time(): Unit = {}

  @Time
  def timeWithoutParameters(): Unit = {}

  @Time
  def timeWithFuture(): Future[String] =
    Future.successful("Hello")

  @Time
  def timeWithCompletionStage(): CompletionStage[String] =
    CompletableFuture.completedFuture("Hello")

  @Time(name = "${'time:' += this.id}", tags = "${'slow-service':'service', 'env':'prod'}")
  def timeWithEL(): Unit = {}

  @Histogram(name = "histogram")
  def histogram(operationName: Long): Long = operationName

  @Histogram(name = "#{'histogram:' += this.id}", tags = "${'histogram':'hdr', 'env':'prod'}")
  def histogramWithEL(operationName: Long): Long = operationName

  def customizeSpan(): Unit = {}
}

object Annotated {
  def apply(): Annotated = new Annotated(0L)
}
