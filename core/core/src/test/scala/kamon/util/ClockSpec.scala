package kamon.util

import kamon.util
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Duration, Instant}

class ClockSpec extends AnyWordSpec with Matchers {
  "the Clock" should {
    "generate nanosecond precision Instants" in {
      newClock().instant().getNano() % MicrosInSecond shouldNot be(0)
    }

    "turn Instants into micros" in {
      Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.000000000Z")) shouldBe 1513586399000000L
      Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.000000010Z")) shouldBe 1513586399000000L
      Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.987654321Z")) shouldBe 1513586399987654L
      Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.987000000Z")) shouldBe 1513586399987000L
    }

    "calculate nanos between two Instants" in {
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654321Z"),
        Instant.parse("2017-12-18T08:39:59.987654322Z")
      ) shouldBe 1
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654322Z"),
        Instant.parse("2017-12-18T08:39:59.987654321Z")
      ) shouldBe -1
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987Z"),
        Instant.parse("2017-12-18T08:39:59.988Z")
      ) shouldBe 1000000
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654Z"),
        Instant.parse("2017-12-18T08:39:59.987Z")
      ) shouldBe -654000
    }

    "calculate ticks aligned to rounded boundaries" in {
      Clock.nextAlignedInstant(
        Instant.parse("2017-12-18T08:39:59.999Z"),
        Duration.ofSeconds(10)
      ).toString shouldBe "2017-12-18T08:40:00Z"
      Clock.nextAlignedInstant(
        Instant.parse("2017-12-18T08:40:00.000Z"),
        Duration.ofSeconds(10)
      ).toString shouldBe "2017-12-18T08:40:10Z"
      Clock.nextAlignedInstant(
        Instant.parse("2017-12-18T08:39:14.906Z"),
        Duration.ofSeconds(10)
      ).toString shouldBe "2017-12-18T08:39:20Z"
    }
  }

  val MicrosInSecond = 1000000

  def newClock(): Clock =
    new util.Clock.Anchored()
}
