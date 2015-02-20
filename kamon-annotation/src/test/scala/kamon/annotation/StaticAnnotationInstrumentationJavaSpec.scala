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

class StaticAnnotationInstrumentationJavaSpec extends BaseKamonSpec("static-annotation-instrumentation-java-spec") {
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
    "create a new trace when is invoked a static method annotated with @Trace" in {
      for (id ← 1 to 10) AnnotatedJavaClass.trace()

      val snapshot = takeSnapshotOf("trace", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
      snapshot.segments.size should be(0)
    }
    "create a segment when is invoked a static method annotated with @Trace and @Segment" in {
      for (id ← 1 to 10) AnnotatedJavaClass.segment()

      val snapshot = takeSnapshotOf("trace-with-segment", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      snapshot.segments.size should be(2)
      snapshot.segment("segment", "segments", "segment") should not be empty
      snapshot.segment("inner-segment", "inner", "segment") should not be empty
    }

    "create a segment when is invoked a static method annotated with @Trace and @Segment and evaluate EL expressions" in {
      for (id ← 1 to 10) AnnotatedJavaClass.segmentWithEL()

      val snapshot = takeSnapshotOf("trace-with-segment-el", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      snapshot.segments.size should be(2)
      snapshot.segment("segment:10", "segments", "segment") should not be empty
      snapshot.segment("inner-segment", "inner", "segment") should not be empty
    }

    "count the invocations of a static method annotated with @Count" in {
      for (id ← 1 to 10) AnnotatedJavaClass.count()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.counter("count").get.count should be(10)
    }

    "count the invocations of a static method annotated with @Count and evaluate EL expressions" in {
      for (id ← 1 to 2) AnnotatedJavaClass.countWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.counter("count:10").get.count should be(2)

      val counterKey = (name: String) ⇒ (key: CounterKey) ⇒ key.name == name

      snapshot.counters.keys.find(counterKey("count:10")).get.metadata should be(Map("counter" -> "1", "env" -> "prod"))
    }

    "count the current invocations of a static method annotated with @MinMaxCount" in {
      for (id ← 1 to 10) {
        AnnotatedJavaClass.countMinMax()
      }

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.minMaxCounter("minMax").get.max should be(1)
    }

    "count the current invocations of a static method annotated with @MinMaxCount and evaluate EL expressions" in {
      for (id ← 1 to 10) AnnotatedJavaClass.countMinMaxWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.minMaxCounter("minMax:10").get.max should be(1)

      val minMaxKey = (name: String) ⇒ (key: MinMaxCounterKey) ⇒ key.name == name

      snapshot.minMaxCounters.keys.find(minMaxKey("minMax:10")).get.metadata should be(Map("minMax" -> "1", "env" -> "dev"))
    }

    "measure the time spent in the execution of a static method annotated with @Time" in {
      for (id ← 1 to 1) AnnotatedJavaClass.time()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("time").get.numberOfMeasurements should be(1)
    }

    "measure the time spent in the execution of a static method annotated with @Time and evaluate EL expressions" in {
      for (id ← 1 to 1) AnnotatedJavaClass.timeWithEL()

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("time:10").get.numberOfMeasurements should be(1)

      val histogramKey = (name: String) ⇒ (key: HistogramKey) ⇒ key.name == name

      snapshot.histograms.keys.find(histogramKey("time:10")).get.metadata should be(Map("slow-service" -> "service", "env" -> "prod"))
    }

    "record the value returned by a static method annotated with @Histogram" in {
      for (value ← 1 to 5) AnnotatedJavaClass.histogram(value.toLong)

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(5)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(5)
      snapshot.histogram("histogram").get.sum should be(15)
    }

    "record the value returned by a static method annotated with @Histogram and evaluate EL expressions" in {
      for (value ← 1 to 2) AnnotatedJavaClass.histogramWithEL(value.toLong)

      val snapshot = takeSnapshotOf("simple-metric", "simple-metric")
      snapshot.histogram("histogram:10").get.numberOfMeasurements should be(2)
      snapshot.histogram("histogram:10").get.min should be(1)
      snapshot.histogram("histogram:10").get.max should be(2)

      val histogramKey = (name: String) ⇒ (key: HistogramKey) ⇒ key.name == name

      snapshot.histograms.keys.find(histogramKey("histogram:10")).get.metadata should be(Map("histogram" -> "hdr", "env" -> "prod"))
    }
  }
}