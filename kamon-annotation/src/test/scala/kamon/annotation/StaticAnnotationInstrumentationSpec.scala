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
  import kamon.metric.TraceMetricsSpec.SegmentSyntax

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
      for (id ← 1 to 10) AnnotatedObject.trace()

      val snapshot = takeSnapshotOf("trace", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
      snapshot.segments.size should be(0)
    }
    "create a segment when is invoked a method annotated with @Trace and @Segment in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.segment()

      val snapshot = takeSnapshotOf("trace-with-segment", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      snapshot.segments.size should be(2)
      snapshot.segment("segment", "segments", "segment") should not be empty
      snapshot.segment("inner-segment", "inner", "segment") should not be empty
    }

    "create a segment when is invoked a method annotated with @Trace and @Segment and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.segmentWithEL()

      val snapshot = takeSnapshotOf("trace-with-segment-el", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      snapshot.segments.size should be(2)
      snapshot.segment("segment:10", "segments", "segment") should not be empty
      snapshot.segment("inner-segment", "inner", "segment") should not be empty
    }

    "count the invocations of a method annotated with @Count in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.count()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.counter("count").get.count should be(10)
    }

    "count the invocations of a method annotated with @Count and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 2) AnnotatedObject.countWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.counter("count:10").get.count should be(2)

      val counterKey = (name: String) ⇒ (key: CounterKey) ⇒ key.name == name

      snapshot.counters.keys.find(counterKey("count:10")).get.metadata should be(Map("counter" -> "1", "env" -> "prod"))
    }

    "count the current invocations of a method annotated with @MinMaxCount in a Scala Object" in {
      for (id ← 1 to 10) {
        AnnotatedObject.countMinMax()
      }

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.minMaxCounter("minMax").get.max should be(1)
    }

    "count the current invocations of a method annotated with @MinMaxCount and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 10) AnnotatedObject.countMinMaxWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.minMaxCounter("minMax:10").get.max should be(1)

      val minMaxKey = (name: String) ⇒ (key: MinMaxCounterKey) ⇒ key.name == name

      snapshot.minMaxCounters.keys.find(minMaxKey("minMax:10")).get.metadata should be(Map("minMax" -> "1", "env" -> "dev"))
    }

    "measure the time spent in the execution of a method annotated with @Time in a Scala Object" in {
      for (id ← 1 to 1) AnnotatedObject.time()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("time").get.numberOfMeasurements should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and evaluate EL expressions in a Scala Object" in {
      for (id ← 1 to 1) AnnotatedObject.timeWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("time:10").get.numberOfMeasurements should be(1)

      val histogramKey = (name: String) ⇒ (key: HistogramKey) ⇒ key.name == name

      snapshot.histograms.keys.find(histogramKey("time:10")).get.metadata should be(Map("slow-service" -> "service", "env" -> "prod"))
    }

    "record the value returned by a method annotated with @Histogram in a Scala Object" in {
      for (value ← 1 to 5) AnnotatedObject.histogram(value)

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(5)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(5)
      snapshot.histogram("histogram").get.sum should be(15)
    }

    "record the value returned by a method annotated with @Histogram and evaluate EL expressions in a Scala Object" in {
      for (value ← 1 to 2) AnnotatedObject.histogramWithEL(value)

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("histogram:10").get.numberOfMeasurements should be(2)
      snapshot.histogram("histogram:10").get.min should be(1)
      snapshot.histogram("histogram:10").get.max should be(2)

      val histogramKey = (name: String) ⇒ (key: HistogramKey) ⇒ key.name == name

      snapshot.histograms.keys.find(histogramKey("histogram:10")).get.metadata should be(Map("histogram" -> "hdr", "env" -> "prod"))
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