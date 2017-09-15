package kamon.util

import org.scalatest.{Matchers, WordSpec}

class DifferentialSourceSpec extends WordSpec with Matchers {

  "a Differential Source" should {
    "get the difference between the last two observations" in {
      val source = sourceOf(0, 0, 1, 1, 2, 3, 4, 6, 8, 10, 12, 16, 18)
      val expectedDiffs = Seq(0, 1, 0, 1, 1, 1, 2, 2, 2, 2, 4, 2)

      values(expectedDiffs.length, source) should contain theSameElementsInOrderAs(expectedDiffs)
    }

    "ignore decrements in observations" in {
      val source = sourceOf(10, 10, 5, 5, 10, 10)
      val expectedDiffs = Seq(0, 0, 0, 5, 0)

      values(expectedDiffs.length, source) should contain theSameElementsInOrderAs(expectedDiffs)
    }
  }

  def sourceOf(numbers: Long*): DifferentialSource = DifferentialSource(new (() => Long) {
    var remaining = numbers.toList

    override def apply(): Long = {
      if(remaining.isEmpty) 0 else {
        val head = remaining.head
        remaining = remaining.tail
        head
      }
    }
  })

  def values(count: Int, source: DifferentialSource): Seq[Long] = {
    for(_ <- 1 to count) yield source.get()
  }
}


