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
import kamon.metric._
import kamon.testkit.BaseKamonSpec
import kamon.trace.SegmentCategory

class AnnotationInstrumentationSpec extends BaseKamonSpec("annotation-instrumentation-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |  default-collection-context-buffer-size = 100
        |}
      """.stripMargin)

  "the Kamon Annotation module" should {
    "create a new trace when is invoked a method annotated with @Trace" in {
      for (id ← 1 to 10) Annotated(id).trace()

      val snapshot = takeSnapshotOf("trace", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
    }

    "create a segment when is invoked a method annotated with @Segment" in {
      for (id ← 1 to 10) Annotated().segment()

      val snapshot = takeSnapshotOf("trace-with-segment", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      val segmentMetricsSnapshot = takeSnapshotOf("inner-segment", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment",
          "category" -> "inner",
          "library" -> "segment"))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)
    }

    "create a segment when is invoked a method annotated with @Segment and evaluate EL expressions" in {
      for (id ← 1 to 10) Annotated(id).segmentWithEL()

      val snapshot = takeSnapshotOf("trace-with-segment-el", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(10)

      val segmentMetricsSnapshot = takeSnapshotOf("inner-segment:1", "trace-segment",
        tags = Map(
          "trace" -> "trace-with-segment-el",
          "category" -> "inner",
          "library" -> "segment"))

      segmentMetricsSnapshot.histogram("elapsed-time").get.numberOfMeasurements should be(1)
    }

    "count the invocations of a method annotated with @Count" in {
      for (id ← 1 to 10) Annotated(id).count()

      val snapshot = takeSnapshotOf("count", "counter")
      snapshot.counter("counter").get.count should be(10)
    }

    "count the invocations of a method annotated with @Count and evaluate EL expressions" in {
      for (id ← 1 to 2) Annotated(id).countWithEL()

      val counter1Snapshot = takeSnapshotOf("count:1", "counter", Map("counter" -> "1", "env" -> "prod"))
      counter1Snapshot.counter("counter").get.count should be(1)

      val counter2Snapshot = takeSnapshotOf("count:2", "counter", Map("counter" -> "1", "env" -> "prod"))
      counter2Snapshot.counter("counter").get.count should be(1)
    }

    "count the current invocations of a method annotated with @MinMaxCount" in {
      for (id ← 1 to 10) {
        Annotated(id).countMinMax()
      }

      val snapshot = takeSnapshotOf("minMax", "min-max-counter")
      snapshot.minMaxCounter("min-max-counter").get.max should be(1)
    }

    "count the current invocations of a method annotated with @MinMaxCount and evaluate EL expressions" in {
      for (id ← 1 to 10) Annotated(id).countMinMaxWithEL()

      val minMaxCounter1Snapshot = takeSnapshotOf("minMax:1", "min-max-counter", tags = Map("minMax" -> "1", "env" -> "dev"))
      minMaxCounter1Snapshot.minMaxCounter("min-max-counter").get.sum should be(1)

      val minMaxCounter2Snapshot = takeSnapshotOf("minMax:2", "min-max-counter", tags = Map("minMax" -> "1", "env" -> "dev"))
      minMaxCounter2Snapshot.minMaxCounter("min-max-counter").get.sum should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time" in {
      for (id ← 1 to 1) Annotated(id).time()

      val snapshot = takeSnapshotOf("time", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "measure the time spent in the execution of a method annotated with @Time and evaluate EL expressions" in {
      for (id ← 1 to 1) Annotated(id).timeWithEL()

      val snapshot = takeSnapshotOf("time:1", "histogram", tags = Map("slow-service" -> "service", "env" -> "prod"))
      snapshot.histogram("histogram").get.numberOfMeasurements should be(1)
    }

    "record the value returned by a method annotated with @Histogram" in {
      for (value ← 1 to 5) Annotated().histogram(value)

      val snapshot = takeSnapshotOf("histogram", "histogram")
      snapshot.histogram("histogram").get.numberOfMeasurements should be(5)
      snapshot.histogram("histogram").get.min should be(1)
      snapshot.histogram("histogram").get.max should be(5)
      snapshot.histogram("histogram").get.sum should be(15)
    }

    "record the value returned by a method annotated with @Histogram and evaluate EL expressions" in {
      for (value ← 1 to 2) Annotated(value).histogramWithEL(value)

      val snapshot1 = takeSnapshotOf("histogram:1", "histogram", tags = Map("histogram" -> "hdr", "env" -> "prod"))
      snapshot1.histogram("histogram").get.numberOfMeasurements should be(1)
      snapshot1.histogram("histogram").get.min should be(1)
      snapshot1.histogram("histogram").get.max should be(1)
      snapshot1.histogram("histogram").get.sum should be(1)

      val snapshot2 = takeSnapshotOf("histogram:2", "histogram", tags = Map("histogram" -> "hdr", "env" -> "prod"))
      snapshot2.histogram("histogram").get.numberOfMeasurements should be(1)
      snapshot2.histogram("histogram").get.min should be(2)
      snapshot2.histogram("histogram").get.max should be(2)
      snapshot2.histogram("histogram").get.sum should be(2)
    }
  }
}

@EnableKamon
case class Annotated(id: Long) {

  @Trace("trace")
  def trace(): Unit = {}

  @Trace("trace-with-segment")
  def segment(): Unit = {
    inner() // method annotated with @Segment
  }

  @Trace("trace-with-segment-el")
  def segmentWithEL(): Unit = {
    innerWithEL() // method annotated with @Segment
  }

  @Count(name = "count")
  def count(): Unit = {}

  @Count(name = "${'count:' += this.id}", tags = "${'counter':'1', 'env':'prod'}")
  def countWithEL(): Unit = {}

  @MinMaxCount(name = "minMax")
  def countMinMax(): Unit = {}

  @MinMaxCount(name = "#{'minMax:' += this.id}", tags = "#{'minMax':'1', 'env':'dev'}")
  def countMinMaxWithEL(): Unit = {}

  @Time(name = "time")
  def time(): Unit = {}

  @Time(name = "${'time:' += this.id}", tags = "${'slow-service':'service', 'env':'prod'}")
  def timeWithEL(): Unit = {}

  @Histogram(name = "histogram")
  def histogram(value: Long): Long = value

  @Histogram(name = "#{'histogram:' += this.id}", tags = "${'histogram':'hdr', 'env':'prod'}")
  def histogramWithEL(value: Long): Long = value

  @Segment(name = "inner-segment", category = "inner", library = "segment")
  private def inner(): Unit = {}

  @Segment(name = "#{'inner-segment:' += this.id}", category = "inner", library = "segment")
  private def innerWithEL(): Unit = {}
}

object Annotated {
  def apply(): Annotated = new Annotated(0L)
}