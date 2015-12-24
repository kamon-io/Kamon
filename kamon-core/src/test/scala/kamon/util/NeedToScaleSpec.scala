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

package kamon.util

import com.typesafe.config.ConfigFactory
import kamon.metric.instrument.{Memory, Time}
import org.scalatest.{Matchers, WordSpec}

class NeedToScaleSpec extends WordSpec with Matchers {

  "NeedToScale" should {
    "extract time unit to scale to from config" in {
      val config = ConfigFactory.parseString(
        """
          |time-units = "ms"
        """.stripMargin)

      config match {
        case NeedToScale(timeUnits, memoryUnits) =>
          timeUnits should be(Some(Time.Milliseconds))
          memoryUnits should be(None)
      }
    }
    "extract memory unit to scale to from config" in {
      val config = ConfigFactory.parseString(
        """
          |memory-units = "kb"
        """.stripMargin)

      config match {
        case NeedToScale(timeUnits, memoryUnits) =>
          timeUnits should be(None)
          memoryUnits should be(Some(Memory.KiloBytes))
      }
    }
    "extract nothing if config has no proper keys" in {
      val config = ConfigFactory.parseString(
        """
          |some-other-key = "value"
        """.stripMargin)

      config match {
        case NeedToScale(timeUnits, memoryUnits) =>
          fail("Should not match")
        case _ =>
      }
    }
  }

}
