/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UtilsOnConfigSpec extends AnyWordSpec with Matchers {
  val config = ConfigFactory.parseString(
    """
      | kamon.test {
      |   configuration-one {
      |     setting = value
      |     other-setting = other-value
      |   }
      |
      |   "config.two" {
      |     setting = value
      |   }
      | }
    """.stripMargin
  )

  "the utils on config syntax" should {
    "list all top level keys with a configuration" in {
      config.getConfig("kamon.test").topLevelKeys should contain only ("configuration-one", "config.two")
    }

    "create a map from top level keys to the inner configuration objects" in {
      val extractedConfigurations = config.getConfig("kamon.test").configurations

      extractedConfigurations.keys should contain only ("configuration-one", "config.two")
      extractedConfigurations("configuration-one").topLevelKeys should contain only ("setting", "other-setting")
      extractedConfigurations("config.two").topLevelKeys should contain only ("setting")
    }
  }

}
