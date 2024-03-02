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

import kamon.annotation.api._
import kamon.module.Module.Registration
import kamon.tag.Lookups.plain
import kamon.tag.TagSet
import kamon.testkit._
import kamon.trace.Span
import kamon.{Kamon, testkit}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues

class StaticAnnotationInstrumentationSpec extends AnyWordSpec
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
    "create a new trace when is invoked a method annotated with @Trace in a Scala Object" in {
      for (_ <- 1 to 10) AnnotatedObject.trace()

      eventually(timeout(10 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "trace"
        spanTags("slow-service") shouldBe "service"
        spanTags("env") shouldBe "prod"
      }
    }

    "count the invocations of a method annotated with @Count without parameters in a Scala Object" in {
      for (_ <- 1 to 10) AnnotatedObject.countWithoutParameters()

      Kamon.counter("kamon.annotation.AnnotatedObject$.countWithoutParameters").withoutTags().value() should be(10)
    }

    "count the invocations of a method annotated with @Count in a Scala Object" in {
      for (_ <- 1 to 10) AnnotatedObject.count()

      Kamon.counter("count").withoutTags().value() should be(10)
    }

    "count the invocations of a method annotated with @Count and evaluate EL expressions in a Scala Object" in {
      for (_ <- 1 to 2) AnnotatedObject.countWithEL()

      Kamon.counter("count:10").withTags(TagSet.from(Map("counter" -> "1", "env" -> "prod"))).value() should be(2)
    }

    "count the current invocations of a method annotated with @TrackConcurrency in a Scala Object" in {
      for (_ <- 1 to 10) {
        AnnotatedObject.countMinMax()
      }

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax").withoutTags().distribution().max should be(0)
      }
    }

    "count the current invocations of a method annotated with @TrackConcurrency and evaluate EL expressions in a Scala Object" in {
      for (_ <- 1 to 10) AnnotatedObject.countMinMaxWithEL()

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax:10").withTags(
          TagSet.from(Map("minMax" -> "1", "env" -> "dev"))
        ).distribution().sum should be(0)
      }
    }

    "measure the time spent in the execution of a method annotated with @Time in a Scala Object" in {
      AnnotatedObject.time()

      Kamon.timer("time").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and evaluate EL expressions in a Scala Object" in {
      AnnotatedObject.timeWithEL()

      Kamon.timer("time:10").withTags(
        TagSet.from(Map("slow-service" -> "service", "env" -> "prod"))
      ).distribution().count should be(1)
    }

    "record the operationName returned by a method annotated with @Histogram in a Scala Object" in {
      for (operationName <- 1 to 5) AnnotatedObject.histogram(operationName)

      val snapshot = Kamon.histogram("histogram").withoutTags().distribution()
      snapshot.count should be(5)
      snapshot.min should be(1)
      snapshot.max should be(5)
      snapshot.sum should be(15)
    }

    "record the operationName returned by a method annotated with @Histogram and evaluate EL expressions in a Scala Object" in {
      for (operationName <- 1 to 2) AnnotatedObject.histogramWithEL(operationName)

      val snapshot =
        Kamon.histogram("histogram:10").withTags(TagSet.from(Map("histogram" -> "hdr", "env" -> "prod"))).distribution()
      snapshot.count should be(2)
      snapshot.min should be(1)
      snapshot.max should be(2)
    }
  }

  @volatile var registration: Registration = _
  val reporter = new testkit.TestSpanReporter.BufferingSpanReporter()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("test-reporter", reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
    super.afterAll()
  }

  def stringTag(span: Span.Finished)(tag: String): String = {
    span.tags.get(plain(tag))
  }
}

object AnnotatedObject {

  val Id = "10"

  @Trace(operationName = "trace", tags = "${'slow-service':'service', 'env':'prod'}")
  def trace(): Unit = {}

  @Count()
  def countWithoutParameters(): Unit = {}

  @Count(name = "count")
  def count(): Unit = {}

  @Count(name = "${'count:' += AnnotatedObject$.MODULE$.Id}", tags = "${'counter':'1', 'env':'prod'}")
  def countWithEL(): Unit = {}

  @TrackConcurrency(name = "minMax")
  def countMinMax(): Unit = {}

  @TrackConcurrency(name = "#{'minMax:' += AnnotatedObject$.MODULE$.Id}", tags = "#{'minMax':'1', 'env':'dev'}")
  def countMinMaxWithEL(): Unit = {}

  @Time(name = "time")
  def time(): Unit = {}

  @Time(name = "${'time:' += AnnotatedObject$.MODULE$.Id}", tags = "${'slow-service':'service', 'env':'prod'}")
  def timeWithEL(): Unit = {}

  @Histogram(name = "histogram")
  def histogram(operationName: Long): Long = operationName

  @Histogram(name = "#{'histogram:' += AnnotatedObject$.MODULE$.Id}", tags = "${'histogram':'hdr', 'env':'prod'}")
  def histogramWithEL(operationName: Long): Long = operationName
}
