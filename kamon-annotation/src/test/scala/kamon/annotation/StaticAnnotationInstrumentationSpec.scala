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

import com.typesafe.config.ConfigFactory
import kamon.metric.{ HistogramKey, MinMaxCounterKey, CounterKey }
import kamon.testkit.BaseKamonSpec

class StaticAnnotationInstrumentationSpec extends BaseKamonSpec("static-annotation-instrumentation-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 100
        |}
      """.stripMargin)
  "the Kamon Annotation module" should {
    "create a new trace when is invoked a method annotated with @Trace in a Scala Object" in {
      for (id ← 1 to 42) AnnotatedObject.trace()

      val snapshot = takeSnapshotOf("trace", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(42)
    }

    "create a segment when is invoked a method annotated with @Trace and @Segment in a Scala Object" in {
      for (id ← 1 to 15) AnnotatedObject.segment()

      val segmentMetricsSnapshot = takeSnapshotOf("segment", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment",
          "category" -> "segments",
          "library" -> "segment"))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(15)
    }

    "create a segment when is invoked a method annotated with @Trace and @Segment and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 18) AnnotatedObject.segmentWithEL()

      val snapshot = takeSnapshotOf("trace-with-segment-el", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(18)

      val segment10Snapshot = takeSnapshotOf("segment:10", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment-el",
          "category" -> "segments",
          "library" -> "segment"))

      segment10Snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(18)

      val innerSegmentSnapshot = takeSnapshotOf("inner-segment", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment-el",
          "category" -> "inner",
          "library" -> "segment"))

      innerSegmentSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(18)
    }

    "count the invocations of a method annotated with @Count in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.count()

      val snapshot = takeSnapshotOf("count", "counter")
      snapshot.counter("counter").get.count should be(10)
    }

    "count the invocations of a method annotated with @Count and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 2) AnnotatedObject.countWithEL()

      val snapshot = takeSnapshotOf("count:10", "counter", tags = Map("counter" -> "1", "env" -> "prod"))
      snapshot.counter("counter").get.count should be(2)

    }

    "count the current invocations of a method annotated with @MinMaxCount in a Scala Object" in {
      for (id ← 1 to 10) {
        AnnotatedObject.countMinMax()
      }

      val snapshot = takeSnapshotOf("minMax", "min-max-counter")
      snapshot.minMaxCounter("min-max-counter").get.max should be(1)
    }

    "count the current invocations of a method annotated with @MinMaxCount and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.countMinMaxWithEL()

      val snapshot = takeSnapshotOf("minMax:10", "min-max-counter", tags = Map("minMax" -> "1", "env" -> "dev"))
      snapshot.minMaxCounter("min-max-counter").get.max should be(1)

    }

    "measure the time spent in the execution of a method annotated with @Time in a Scala Object" in {
      for (id ← 1 to 1) AnnotatedObject.time()

      val snapshot = takeSnapshotOf("time", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 1) AnnotatedObject.timeWithEL()

      val snapshot = takeSnapshotOf("time:10", "histogram", tags = Map("slow-service" -> "service", "env" -> "prod"))
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "record the value returned by a method annotated with @Histogram in a Scala Object" in {
      for (value ← 1 to 5) AnnotatedObject.histogram(value)

      val snapshot = takeSnapshotOf("histogram", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(5)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(5)
      snapshot.histogram("histogram").get.sum should be(15)
    }

    "record the value returned by a method annotated with @Histogram and evaluate EL expressions in a Scala Object" in {
      for (value ← 1 to 2) AnnotatedObject.histogramWithEL(value)

      val snapshot = takeSnapshotOf("histogram:10", "histogram", tags = Map("histogram" -> "hdr", "env" -> "prod"))
      snapshot.histogram("histogram").get.numberOfMeasurements should be(2)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(2)
    }
  }
}

@EnableKamon
object AnnotatedObject {

  val Id = "10"

  @Trace("trace")
  def trace(): Unit = {}

  @Trace("trace-with-segment")
  @Segment(name = "segment", category = "segments", library = "segment")
  def segment(): Unit = {
    inner() // method annotated with @Segment
  }

  @Trace("trace-with-segment-el")
  @Segment(name = "#{'segment:' += AnnotatedObject$.MODULE$.Id}", category = "segments", library = "segment")
  def segmentWithEL(): Unit = {
    inner() // method annotated with @Segment
  }

  @Count(name = "count")
  def count(): Unit = {}

  @Count(name = "${'count:' += AnnotatedObject$.MODULE$.Id}", tags = "${'counter':'1', 'env':'prod'}")
  def countWithEL(): Unit = {}

  @MinMaxCount(name = "minMax")
  def countMinMax(): Unit = {}

  @MinMaxCount(name = "#{'minMax:' += AnnotatedObject$.MODULE$.Id}", tags = "#{'minMax':'1', 'env':'dev'}")
  def countMinMaxWithEL(): Unit = {}

  @Time(name = "time")
  def time(): Unit = {}

  @Time(name = "${'time:' += AnnotatedObject$.MODULE$.Id}", tags = "${'slow-service':'service', 'env':'prod'}")
  def timeWithEL(): Unit = {}

  @Histogram(name = "histogram")
  def histogram(value: Long): Long = value

  @Histogram(name = "#{'histogram:' += AnnotatedObject$.MODULE$.Id}", tags = "${'histogram':'hdr', 'env':'prod'}")
  def histogramWithEL(value: Long): Long = value

  @Segment(name = "inner-segment", category = "inner", library = "segment")
  private def inner(): Unit = {}
}