package kamon.statsd

import kamon.metric.MeasurementUnit.{information, time}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ReadConfigUnitSpec extends AnyWordSpec with Matchers {

  "time unit config" should {

    "read seconds" in {
      readTimeUnit("s") should be(time.seconds)
    }

    "read milliseconds" in {
      readTimeUnit("ms") should be(time.milliseconds)
    }

    "read microseconds" in {
      readTimeUnit("µs") should be(time.microseconds)
    }

    "read nanoseconds" in {
      readTimeUnit("ns") should be(time.nanoseconds)
    }

    "not read other units" in {
      val error = intercept[RuntimeException] { readTimeUnit("h") }
      error.getMessage should include("Invalid time unit")
      error.getMessage should include("[h]")
    }

  }

  "information unit config" should {

    "read bytes" in {
      readInformationUnit("b") should be(information.bytes)
    }

    "read kilobytes" in {
      readInformationUnit("kb") should be(information.kilobytes)
    }

    "read megabytes" in {
      readInformationUnit("mb") should be(information.megabytes)
    }

    "read gigabytes" in {
      readInformationUnit("gb") should be(information.gigabytes)
    }

    "not read other units" in {
      val error = intercept[RuntimeException] { readInformationUnit("tb") }
      error.getMessage should include("Invalid information unit")
      error.getMessage should include("[tb]")
    }

  }

}
