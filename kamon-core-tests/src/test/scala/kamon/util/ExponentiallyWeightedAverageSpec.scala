package kamon.util

import org.scalactic.TimesOnInt
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class ExponentiallyWeightedAverageSpec extends WordSpec with Matchers with Eventually with SpanSugar with TimesOnInt {

  "an Exponentially Weighted Moving Average" should {
    "converge to the actual average in few iterations from startup with the default weighting factor" in {
      val ewma = EWMA.create()
      ewma.add(60D)
      ewma.add(40D)
      ewma.add(55D)
      ewma.add(45D)
      ewma.add(50D)
      ewma.add(50D)
      ewma.add(50D)

      ewma.average() shouldBe 50D +- 5D
    }


    "catch up with an up trend" in {
      val ewma = EWMA.create()
      var value = 500

      200 times {
        value = value + 5
        ewma.add(value)
      }

      ewma.average() shouldBe value.toDouble +- 50D
    }

    "take many iterations to converge on the average with a high weighting factor" in {
      val ewma = EWMA.create(0.99D)
      ewma.add(60D)
      ewma.add(40D)
      ewma.add(50D)
      ewma.add(50D)
      ewma.add(50D)

      30 times {
        ewma.add(50D)
        ewma.add(50D)
        ewma.add(50D)
      }

      ewma.average() shouldBe 50D +- 5D
    }

  }

}
