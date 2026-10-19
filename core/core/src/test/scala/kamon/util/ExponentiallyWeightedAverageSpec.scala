package kamon.util

import org.scalactic.TimesOnInt
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec

class ExponentiallyWeightedAverageSpec extends AnyWordSpec with Matchers with Eventually with SpanSugar
    with TimesOnInt {

  "an Exponentially Weighted Moving Average" should {
    "converge to the actual average in few iterations from startup with the default weighting factor" in {
      val ewma = EWMA.create()
      ewma.add(60d)
      ewma.add(40d)
      ewma.add(55d)
      ewma.add(45d)
      ewma.add(50d)
      ewma.add(50d)
      ewma.add(50d)

      ewma.average() shouldBe 50d +- 5d
    }

    "catch up with an up trend" in {
      val ewma = EWMA.create()
      var value = 500

      200 times {
        value = value + 5
        ewma.add(value)
      }

      ewma.average() shouldBe value.toDouble +- 50d
    }

    "take many iterations to converge on the average with a high weighting factor" in {
      val ewma = EWMA.create(0.99d)
      ewma.add(60d)
      ewma.add(40d)
      ewma.add(50d)
      ewma.add(50d)
      ewma.add(50d)

      30 times {
        ewma.add(50d)
        ewma.add(50d)
        ewma.add(50d)
      }

      ewma.average() shouldBe 50d +- 5d
    }

  }

}
