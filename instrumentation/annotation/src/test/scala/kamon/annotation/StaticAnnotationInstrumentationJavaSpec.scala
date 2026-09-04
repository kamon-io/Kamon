/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import kamon.{Kamon, testkit}
import kamon.module.Module.Registration
import kamon.tag.Lookups._
import kamon.tag.TagSet
import kamon.testkit._
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec

class StaticAnnotationInstrumentationJavaSpec extends AnyWordSpec
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
    "create a new trace when is invoked a static method annotated with @Trace" in {
      for (_ <- 1 to 10) AnnotatedJavaClass.trace()

      eventually(timeout(5 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "trace"
        spanTags("slow-service") shouldBe "service"
        spanTags("env") shouldBe "prod"
      }
    }

    "count the invocations of a static method annotated with @Count without parameters" in {
      for (_ <- 1 to 10) AnnotatedJavaClass.countWithoutParameters()

      Kamon.counter("kamon.annotation.AnnotatedJavaClass.countWithoutParameters").withoutTags().value() should be(10)
    }

    "count the invocations of a static method annotated with @Count" in {
      for (_ <- 1 to 10) AnnotatedJavaClass.count()

      Kamon.counter("count").withoutTags().value() should be(10)
    }

    "count the invocations of a static method annotated with @Count and evaluate EL expressions" in {
      for (_ <- 1 to 2) AnnotatedJavaClass.countWithEL()

      Kamon.counter("count:10").withTags(TagSet.from(Map("counter" -> "1", "env" -> "prod"))).value() should be(2)
    }

    "count the current invocations of a static method annotated with @TrackConcurrency" in {
      for (_ <- 1 to 10) AnnotatedJavaClass.countMinMax()

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax").withoutTags().distribution().max should be(0)
      }
    }

    "count the current invocations of a static method annotated with @TrackConcurrency and evaluate EL expressions" in {
      for (_ <- 1 to 10) AnnotatedJavaClass.countMinMaxWithEL()

      eventually(timeout(5 seconds)) {
        Kamon.rangeSampler("minMax:10").withTags(
          TagSet.from(Map("minMax" -> "1", "env" -> "dev"))
        ).distribution().sum should be(0)
      }
    }

    "measure the time spent in the execution of a static method annotated with @Time" in {
      for (_ <- 1 to 1) AnnotatedJavaClass.time()

      Kamon.timer("time").withoutTags().distribution().count should be(1)
    }

    "measure the time spent in the execution of a static method annotated with @Time and evaluate EL expressions" in {
      for (_ <- 1 to 1) AnnotatedJavaClass.timeWithEL()

      Kamon.timer("time:10").withTags(
        TagSet.from(Map("slow-service" -> "service", "env" -> "prod"))
      ).distribution().count should be(1)
    }

    "record the operationName returned by a static method annotated with @Histogram" in {
      for (operationName <- 1 to 5) AnnotatedJavaClass.histogram(operationName.toLong)

      val snapshot = Kamon.histogram("histogram").withoutTags().distribution()
      snapshot.count should be(5)
      snapshot.min should be(1)
      snapshot.max should be(5)
      snapshot.sum should be(15)
    }

    "record the operationName returned by a static method annotated with @Histogram and evaluate EL expressions" in {
      for (operationName <- 1 to 2) AnnotatedJavaClass.histogramWithEL(operationName.toLong)

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
