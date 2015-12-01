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

import kamon.metric.instrument.UnitOfMeasurement.Unknown
import org.scalatest.{Matchers, WordSpec}

class UnitOfMeasurementSpec extends WordSpec with Matchers {

  "Time unit" should {
    "resolve Time Unit by valid name" in {
      Time("s") should be(Time.Seconds)
      Time("n") should be(Time.Nanoseconds)
      Time("ms") should be(Time.Milliseconds)
      Time("µs") should be(Time.Microseconds)
    }
    "fail to resolve Time Unit by invalid name" in {
      val ex = intercept[IllegalArgumentException](Time("boo"))
      ex.getMessage should be("Can't recognize time unit 'boo'")
    }
    "scale time properly" in {
      val epsilon = 0.0001

      Time.Nanoseconds.scale(Time.Nanoseconds)(1000000D) should be(1000000D +- epsilon)
      Time.Nanoseconds.scale(Time.Microseconds)(1000000D) should be(1000D +- epsilon)
      Time.Nanoseconds.scale(Time.Milliseconds)(1000000D) should be(1D +- epsilon)
      Time.Nanoseconds.scale(Time.Seconds)(1000000D) should be(0.001D +- epsilon)
      Time.Seconds.scale(Time.Nanoseconds)(1D) should be(1000000000D +- epsilon)
    }
    "allow scale only time" in {
      intercept[IllegalArgumentException](Time.Nanoseconds.tryScale(Unknown)(100))
        .getMessage should be("Can't scale different types of units `time` and `unknown`")
      intercept[IllegalArgumentException](Time.Nanoseconds.tryScale(Memory.Bytes)(100))
        .getMessage should be("Can't scale different types of units `time` and `bytes`")
      val epsilon = 0.0001

      Time.Nanoseconds.tryScale(Time.Nanoseconds)(100D) should be(100D +- epsilon)
    }
  }

  "Memory unit" should {
    "resolve Memory Unit by valid name" in {
      Memory("b") should be(Memory.Bytes)
      Memory("Kb") should be(Memory.KiloBytes)
      Memory("Mb") should be(Memory.MegaBytes)
      Memory("Gb") should be(Memory.GigaBytes)
    }
    "fail to resolve Memory Unit by invalid name" in {
      val ex = intercept[IllegalArgumentException](Memory("boo"))
      ex.getMessage should be("Can't recognize memory unit 'boo'")
    }
    "scale memory properly" in {
      val epsilon = 0.0001

      Memory.Bytes.scale(Memory.Bytes)(1000000D) should be(1000000D +- epsilon)
      Memory.Bytes.scale(Memory.KiloBytes)(1000000D) should be(976.5625D +- epsilon)
      Memory.Bytes.scale(Memory.MegaBytes)(1000000D) should be(0.9536D +- epsilon)
      Memory.Bytes.scale(Memory.GigaBytes)(1000000D) should be(9.3132E-4D +- epsilon)
      Memory.MegaBytes.scale(Memory.Bytes)(1D) should be(1048576D +- epsilon)
    }
    "allow scale only memory" in {
      intercept[IllegalArgumentException](Memory.Bytes.tryScale(Unknown)(100))
        .getMessage should be("Can't scale different types of units `bytes` and `unknown`")
      intercept[IllegalArgumentException](Memory.Bytes.tryScale(Time.Nanoseconds)(100))
        .getMessage should be("Can't scale different types of units `bytes` and `time`")
      val epsilon = 0.0001

      Memory.Bytes.tryScale(Memory.Bytes)(100D) should be(100D +- epsilon)
    }

  }

  "Unknown unit" should {
    "allow scale only Unknown" in {
      intercept[IllegalArgumentException](Unknown.tryScale(Memory.Bytes)(100))
        .getMessage should be("Can't scale different types of units `unknown` and `bytes`")
      intercept[IllegalArgumentException](Unknown.tryScale(Time.Nanoseconds)(100))
        .getMessage should be("Can't scale different types of units `unknown` and `time`")

      Unknown.scale(Unknown)(100D) should be(100D)
    }

  }
}
