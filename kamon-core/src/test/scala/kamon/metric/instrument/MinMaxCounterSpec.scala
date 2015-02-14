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

import akka.actor._
import akka.testkit.TestProbe
import kamon.Kamon
import kamon.metric.instrument.Histogram.{ DynamicRange, MutableRecord }
import kamon.testkit.BaseKamonSpec
import scala.concurrent.duration._

class MinMaxCounterSpec extends BaseKamonSpec("min-max-counter-spec") {

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

    "report zero as the min and current values if the current value fell bellow zero" in new MinMaxCounterFixture {
      mmCounter.decrement(3)

      val snapshot = collectCounterSnapshot()

      snapshot.min should be(0)
      snapshot.max should be(0)
      snapshot.recordsIterator.toStream should contain(
        MutableRecord(0, 3)) // min, max and current (even while current really is -3
    }

    "never record values bellow zero in very busy situations" in new MinMaxCounterFixture {
      val monitor = TestProbe()
      val workers = for (workers ← 1 to 50) yield {
        system.actorOf(Props(new MinMaxCounterUpdateActor(mmCounter, monitor.ref)))
      }

      workers foreach (_ ! "increment")
      for (refresh ← 1 to 1000) {
        collectCounterSnapshot()
        Thread.sleep(1)
      }

      monitor.expectNoMsg()
      workers foreach (_ ! PoisonPill)
    }
  }

  trait MinMaxCounterFixture {
    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(64)
    }

    val mmCounter = MinMaxCounter(DynamicRange(1, 1000, 2), 1 hour, Kamon.metrics.settings.refreshScheduler)
    mmCounter.cleanup // cancel the refresh schedule

    def collectCounterSnapshot(): Histogram.Snapshot = mmCounter.collect(collectionContext)
  }
}

class MinMaxCounterUpdateActor(mmc: MinMaxCounter, monitor: ActorRef) extends Actor {
  val x = Array.ofDim[Int](4)
  def receive = {
    case "increment" ⇒
      mmc.increment()
      self ! "decrement"
    case "decrement" ⇒
      mmc.decrement()
      self ! "increment"
      try {
        mmc.refreshValues()
      } catch {
        case _: IndexOutOfBoundsException ⇒ monitor ! "failed"
      }
  }
}