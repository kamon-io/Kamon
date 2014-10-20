/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metric.instrument

import java.nio.LongBuffer

import com.typesafe.config.ConfigFactory
import kamon.metric.CollectionContext
import org.scalatest.{ Matchers, WordSpec }

import scala.util.Random

class HistogramSpec extends WordSpec with Matchers {

  val histogramConfig = ConfigFactory.parseString(
    """
      |
      |highest-trackable-value = 100000
      |significant-value-digits = 2
      |
    """.stripMargin)

  "a Histogram" should {
    "allow record values within the configured range" in new HistogramFixture {
      histogram.record(1000)
      histogram.record(5000, count = 100)
      histogram.record(10000)
    }

    "fail when recording values higher than the highest trackable value" in new HistogramFixture {
      intercept[IndexOutOfBoundsException] {
        histogram.record(1000000)
      }
    }

    "reset all recorded levels to zero after a snapshot collection" in new HistogramFixture {
      histogram.record(100)
      histogram.record(200)
      histogram.record(300)

      takeSnapshot().numberOfMeasurements should be(3)
      takeSnapshot().numberOfMeasurements should be(0)
    }

    "produce a snapshot" which {
      "supports min, max, percentile, sum and numberOfMeasurements operations" in new HistogramFixture {
        histogram.record(100)
        histogram.record(200, count = 200)
        histogram.record(300)
        histogram.record(900)

        val snapshot = takeSnapshot()

        snapshot.min should equal(100L +- 1L)
        snapshot.max should equal(900L +- 9L)
        snapshot.percentile(50.0D) should be(200)
        snapshot.percentile(99.5D) should be(300)
        snapshot.percentile(99.9D) should be(900)
        snapshot.sum should be(41300)
        snapshot.numberOfMeasurements should be(203)

      }

      "can be merged with another snapshot" in new MultipleHistogramFixture {
        val random = new Random(System.nanoTime())

        for (repetitions ← 1 to 1000) {
          // Put some values on A and Control
          for (_ ← 1 to 1000) {
            val newRecording = random.nextInt(100000)
            controlHistogram.record(newRecording)
            histogramA.record(newRecording)
          }

          // Put some values on B and Control
          for (_ ← 1 to 2000) {
            val newRecording = random.nextInt(100000)
            controlHistogram.record(newRecording)
            histogramB.record(newRecording)
          }

          val controlSnapshot = takeSnapshotFrom(controlHistogram)
          val histogramASnapshot = takeSnapshotFrom(histogramA)
          val histogramBSnapshot = takeSnapshotFrom(histogramB)

          assertEquals(controlSnapshot, histogramASnapshot.merge(histogramBSnapshot, collectionContext))
          assertEquals(controlSnapshot, histogramBSnapshot.merge(histogramASnapshot, collectionContext))
        }
      }
    }
  }

  trait HistogramFixture {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    val histogram = Histogram.fromConfig(histogramConfig)

    def takeSnapshot(): Histogram.Snapshot = histogram.collect(collectionContext)
  }

  trait MultipleHistogramFixture {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    val controlHistogram = Histogram.fromConfig(histogramConfig)
    val histogramA = Histogram.fromConfig(histogramConfig)
    val histogramB = Histogram.fromConfig(histogramConfig)

    def takeSnapshotFrom(histogram: Histogram): Histogram.Snapshot = histogram.collect(collectionContext)

    def assertEquals(left: Histogram.Snapshot, right: Histogram.Snapshot): Unit = {
      left.numberOfMeasurements should equal(right.numberOfMeasurements)
      left.min should equal(right.min)
      left.max should equal(right.max)
      left.recordsIterator.toStream should contain theSameElementsAs (right.recordsIterator.toStream)
    }
  }
}
