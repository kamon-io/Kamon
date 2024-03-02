/* =========================================================================================
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

package kamon.trace

import java.time.Instant

import kamon.Kamon._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection, Reconfigure}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.control.NoStackTrace

class SpanMetricsSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax with MetricInspection.Syntax
    with Reconfigure with InitAndStopKamonAfterAll {

  sampleNever()

  "the Span Metrics" should {
    "track span.processing-time for successful execution on a Span" in {
      val operation = "span-success"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .start()
        .finish()

      val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag)))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
      errorHistogram.distribution().count shouldBe 0

    }

    "not track span.processing-time when doNotTrackProcessingTime() is called on the SpanBuilder or the Span" in {
      val operation = "span-with-disabled-metrics"
      spanBuilder(operation)
        .start()
        .doNotTrackMetrics()
        .finish()

      spanBuilder(operation)
        .doNotTrackMetrics()
        .start()
        .finish()

      Span.Metrics.ProcessingTime.tagValues("operation") shouldNot contain(operation)
    }

    "allow specifying custom Span metric tags" in {
      val operation = "span-with-custom-metric-tags"
      spanBuilder(operation)
        .tagMetrics("custom-metric-tag-on-builder", "value")
        .start()
        .tagMetrics("custom-metric-tag-on-span", "value")
        .finish()

      Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-builder") should contain("value")
      Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-span") should contain("value")
    }

    "track span.processing-time if enabled by calling trackProcessingTime() on the Span" in {
      val operation = "span-with-re-enabled-metrics"
      spanBuilder(operation)
        .start()
        .doNotTrackMetrics()
        .trackMetrics()
        .finish()

      Span.Metrics.ProcessingTime.tagValues("operation") should contain(operation)
    }

    "track span.processing-time for failed execution on a Span" in {
      val operation = "span-failure"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .start()
        .fail("Terrible Error")
        .finish()

      spanBuilder(operation)
        .start()
        .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
        .finish()

      val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag)))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
      errorHistogram.distribution().count shouldBe 2
    }

    "add a parentOperation tag to the metrics if span metrics scoping is enabled" in {
      val parent = spanBuilder("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error")
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
        .finish()

      val histogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
      histogram.distribution().count shouldBe 1

      val errorHistogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
      errorHistogram.distribution().count shouldBe 2
    }

    "not add any parentOperation tag to the metrics if span metrics scoping is disabled" in withoutSpanScopingEnabled {
      val parent = spanBuilder("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error")
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
        .finish()

      val histogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
      histogram.distribution().count shouldBe 0

      val errorHistogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
      errorHistogram.distribution().count shouldBe 0
    }

    "track span.elapsed-time and span.wait-time for delayed spans" in {
      val createTime = Instant.ofEpochSecond(0)
      val operation = "delayed-span-success"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .delay(createTime)
        .start(createTime.plusNanos(10))
        .finish(createTime.plusNanos(30))

      val waitTime = Span.Metrics.WaitTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()
      val elapsedTime = Span.Metrics.ElapsedTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()
      val processingTime =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()

      waitTime.count shouldBe 1
      waitTime.buckets.head.value shouldBe 10

      elapsedTime.count shouldBe 1
      elapsedTime.buckets.head.value shouldBe 30

      processingTime.count shouldBe 1
      processingTime.buckets.head.value shouldBe 20
    }

    "include the span kind tag on all Span metrics" in {
      val operation = "span-with-kind"

      spanBuilder(operation).start().finish()
      serverSpanBuilder(operation, "test").delay().start().finish()
      clientSpanBuilder(operation, "test").delay().start().finish()
      producerSpanBuilder(operation, "test").delay().start().finish()
      consumerSpanBuilder(operation, "test").delay().start().finish()
      internalSpanBuilder(operation, "test").delay().start().finish()

      val expectedSpanKinds = Seq(
        "client",
        "server",
        "producer",
        "consumer",
        "internal"
      )

      Span.Metrics.ProcessingTime.tagValues("span.kind") should contain only (expectedSpanKinds: _*)
      Span.Metrics.ElapsedTime.tagValues("span.kind") should contain only (expectedSpanKinds: _*)
      Span.Metrics.WaitTime.tagValues("span.kind") should contain only (expectedSpanKinds: _*)
    }
  }

  val errorTag = "error" -> true
  val noErrorTag = "error" -> false

  private def withoutSpanScopingEnabled[T](f: => T): T = {
    disableSpanMetricScoping()
    val evaluated = f
    enableSpanMetricScoping()
    evaluated
  }
}
