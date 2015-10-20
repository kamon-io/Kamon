package kamon.util

import com.typesafe.config.ConfigFactory
import kamon.metric.instrument.{ Memory, Time }
import org.scalatest.{ Matchers, WordSpec }

class ScaleUnitsSpec extends WordSpec with Matchers {

  "ScaleUnits" should {
    "read time unit to scale to from config and scale properly" in {
      val scaler = new ScaleUnits(
        ConfigFactory.parseString("""
                                     |time-units = "ms"
                                   """.stripMargin))

      scaler.scaleMemoryTo should be(None)
      scaler.scaleTimeTo should be(Some(Time.Milliseconds))

      scaler.scale(Time.Nanoseconds, 1000000) should be(1)
      scaler.scale(Memory.Bytes, 1000000) should be(1000000)
    }
    "read memory unit to scale to from config and scale properly" in {
      val scaler = new ScaleUnits(
        ConfigFactory.parseString("""
                                     |memory-units = "kb"
                                   """.stripMargin))

      scaler.scaleMemoryTo should be(Some(Memory.KiloBytes))
      scaler.scaleTimeTo should be(None)

      scaler.scale(Time.Nanoseconds, 1000000) should be(1000000)
      scaler.scale(Memory.Bytes, 10240) should be(10)
    }
  }

}
