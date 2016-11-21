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
    }
    "create a segment when is invoked a static method annotated with @Segment" in {
      for (id ← 1 to 7) AnnotatedJavaClass.segment()

      val segmentMetricsSnapshot = takeSnapshotOf("inner-segment", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment",
          "category" -> "inner",
          "library" -> "segment"))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(7)
    }

    "create a segment when is invoked a static method annotated with @Segment and evaluate EL expressions" in {
      for (id ← 1 to 10) AnnotatedJavaClass.segmentWithEL()

      val snapshot = takeSnapshotOf("trace-with-segment-el", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      val segmentMetricsSnapshot = takeSnapshotOf("inner-segment:10", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment-el",
          "category" -> "segments",
          "library" -> "segment"))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
    }

    "count the invocations of a static method annotated with @Count" in {
      for (id ← 1 to 10) AnnotatedJavaClass.count()

      val snapshot = takeSnapshotOf("count", "counter")
      snapshot.counter("counter").get.count should be(10)
    }

    "count the invocations of a static method annotated with @Count and evaluate EL expressions" in {
      for (id ← 1 to 2) AnnotatedJavaClass.countWithEL()

      val snapshot = takeSnapshotOf("count:10", "counter", tags = Map("counter" -> "1", "env" -> "prod"))
      snapshot.counter("counter").get.count should be(2)
    }

    "count the current invocations of a static method annotated with @MinMaxCount" in {
      for (id ← 1 to 10) {
        AnnotatedJavaClass.countMinMax()
      }

      val snapshot = takeSnapshotOf("minMax", "min-max-counter")
      snapshot.minMaxCounter("min-max-counter").get.max should be(1)
    }

    "count the current invocations of a static method annotated with @MinMaxCount and evaluate EL expressions" in {
      for (id ← 1 to 10) AnnotatedJavaClass.countMinMaxWithEL()

      val snapshot = takeSnapshotOf("minMax:10", "min-max-counter", tags = Map("minMax" -> "1", "env" -> "dev"))
      snapshot.minMaxCounter("min-max-counter").get.max should be(1)
    }

    "measure the time spent in the execution of a static method annotated with @Time" in {
      for (id ← 1 to 1) AnnotatedJavaClass.time()

      val snapshot = takeSnapshotOf("time", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "measure the time spent in the execution of a static method annotated with @Time and evaluate EL expressions" in {
      for (id ← 1 to 1) AnnotatedJavaClass.timeWithEL()

      val snapshot = takeSnapshotOf("time:10", "histogram", tags = Map("slow-service" -> "service", "env" -> "prod"))
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "record the value returned by a static method annotated with @Histogram" in {
      for (value ← 1 to 5) AnnotatedJavaClass.histogram(value.toLong)

      val snapshot = takeSnapshotOf("histogram", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(5)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(5)
      snapshot.histogram("histogram").get.sum should be(15)
    }

    "record the value returned by a static method annotated with @Histogram and evaluate EL expressions" in {
      for (value ← 1 to 2) AnnotatedJavaClass.histogramWithEL(value.toLong)

      val snapshot = takeSnapshotOf("histogram:10", "histogram", tags = Map("histogram" -> "hdr", "env" -> "prod"))
      snapshot.histogram("histogram").get.numberOfMeasurements should be(2)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(2)
    }
  }
}