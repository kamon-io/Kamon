/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import kamon.metric.CollectionContext
import kamon.metric.instrument.Histogram.MutableRecord
import org.scalatest.{ Matchers, WordSpecLike }

class MinMaxCounterSpec extends WordSpecLike with Matchers {
  val system = ActorSystem("min-max-counter-spec")
  val minMaxCounterConfig = ConfigFactory.parseString(
    """
      |refresh-interval = 1 hour
      |highest-trackable-value = 1000
      |significant-value-digits = 2
    """.stripMargin)

  "the MinMaxCounter" should {
    "track ascending tendencies" in new MinMaxCounterFixture {
      mmCounter.increment()
      mmCounter.increment(3)
      mmCounter.increment()

      val snapshot = collectCounterSnapshot()

      snapshot.min should be(0)
      snapshot.max should be(5)
      snapshot.recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min
        MutableRecord(5, 2)) // max and current
    }

    "track descending tendencies" in new MinMaxCounterFixture {
      mmCounter.increment(5)
      mmCounter.decrement()
      mmCounter.decrement(3)
      mmCounter.decrement()

      val snapshot = collectCounterSnapshot()

      snapshot.min should be(0)
      snapshot.max should be(5)
      snapshot.recordsIterator.toStream should contain allOf (
        MutableRecord(0, 2), // min and current
        MutableRecord(5, 1)) // max
    }

    "reset the min and max to the current value after taking a snapshot" in new MinMaxCounterFixture {
      mmCounter.increment(5)
      mmCounter.decrement(3)

      val firstSnapshot = collectCounterSnapshot()

      firstSnapshot.min should be(0)
      firstSnapshot.max should be(5)
      firstSnapshot.recordsIterator.toStream should contain allOf (
        MutableRecord(0, 1), // min
        MutableRecord(2, 1), // current
        MutableRecord(5, 1)) // max

      val secondSnapshot = collectCounterSnapshot()

      secondSnapshot.min should be(2)
      secondSnapshot.max should be(2)
      secondSnapshot.recordsIterator.toStream should contain(
        MutableRecord(2, 3)) // min, max and current
    }

    "report zero as the min and current values if they current value fell bellow zero" in new MinMaxCounterFixture {
      mmCounter.decrement(3)

      val snapshot = collectCounterSnapshot()

      snapshot.min should be(0)
      snapshot.max should be(0)
      snapshot.recordsIterator.toStream should contain(
        MutableRecord(0, 3)) // min, max and current (even while current really is -3
    }
  }

  trait MinMaxCounterFixture {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(64)
    }

    val mmCounter = MinMaxCounter.fromConfig(minMaxCounterConfig, system).asInstanceOf[PaddedMinMaxCounter]
    mmCounter.cleanup // cancel the refresh schedule

    def collectCounterSnapshot(): Histogram.Snapshot = mmCounter.collect(collectionContext)
  }
}
