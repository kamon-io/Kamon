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

package kamon.metric.instrument

import java.nio.LongBuffer

import org.scalatest.{ Matchers, WordSpec }

class CounterSpec extends WordSpec with Matchers {

  "a Counter" should {
    "allow increment only operations" in new CounterFixture {
      counter.increment()
      counter.increment(10)

      intercept[UnsupportedOperationException] {
        counter.increment(-10)
      }
    }

    "reset to zero when a snapshot is taken" in new CounterFixture {
      counter.increment(100)
      takeSnapshotFrom(counter).count should be(100)
      takeSnapshotFrom(counter).count should be(0)
      takeSnapshotFrom(counter).count should be(0)

      counter.increment(50)
      takeSnapshotFrom(counter).count should be(50)
      takeSnapshotFrom(counter).count should be(0)
    }

    "produce a snapshot that can be merged with others" in new CounterFixture {
      val counterA = Counter()
      val counterB = Counter()
      counterA.increment(100)
      counterB.increment(200)

      val counterASnapshot = takeSnapshotFrom(counterA)
      val counterBSnapshot = takeSnapshotFrom(counterB)

      counterASnapshot.merge(counterBSnapshot, collectionContext).count should be(300)
      counterBSnapshot.merge(counterASnapshot, collectionContext).count should be(300)
    }

    "produce a snapshot that can be scaled" in new CounterFixture {
      counter.increment(100)

      val counterSnapshot = takeSnapshotFrom(counter)

      val scaledSnapshot = counterSnapshot.scale(Time.Milliseconds, Time.Microseconds)
      scaledSnapshot.count should be(100000)
    }

  }

  trait CounterFixture {
    val counter = Counter()

    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(1)
    }

    def takeSnapshotFrom(counter: Counter): Counter.Snapshot = counter.collect(collectionContext)
  }
}
