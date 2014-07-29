package kamon.metric.instrument

import java.nio.LongBuffer

import kamon.metric.CollectionContext
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

  }

  trait CounterFixture {
    val counter = Counter()

    val collectionContext = new CollectionContext {
      val buffer: LongBuffer = LongBuffer.allocate(1)
    }

    def takeSnapshotFrom(counter: Counter): Counter.Snapshot = counter.collect(collectionContext)
  }
}
