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

package kamon.status

import com.typesafe.config.ConfigFactory
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EnvironmentSpec extends AnyWordSpec with Matchers {
  private val baseConfig = ConfigFactory.parseString(
    """
      |kamon.environment {
      |  service = environment-spec
      |  host = auto
      |  instance = auto
      |}
    """.stripMargin
  ).withFallback(ConfigFactory.defaultReference())

  "the Kamon environment" should {
    "assign a host and instance name when they are set to 'auto'" in {
      val env = Environment.from(baseConfig)

      env.host shouldNot be("auto")
      env.instance shouldNot be("auto")
      env.instance shouldBe s"environment-spec@${env.host}"
      env.tags shouldBe empty
    }

    "use the configured host and instance, if provided" in {
      val customConfig = ConfigFactory.parseString(
        """
          |kamon.environment {
          |  host = spec-host
          |  instance = spec-instance
          |}
        """.stripMargin
      )

      val env = Environment.from(customConfig.withFallback(baseConfig))

      env.host should be("spec-host")
      env.instance should be("spec-instance")
      env.tags shouldBe empty
    }

    "read all environment tags, if provided" in {
      val customConfig = ConfigFactory.parseString(
        """
          |kamon.environment.tags {
          |  custom1 = "test1"
          |  env = staging
          |}
        """.stripMargin
      )

      val env = Environment.from(customConfig.withFallback(baseConfig))

      env.tags.toMap should contain allOf (
        ("custom1" -> "test1"),
        ("env" -> "staging")
      )
    }

    "always return the same incarnation name" in {
      val envOne = Environment.from(baseConfig)
      val envTwo = Environment.from(baseConfig)

      envOne.incarnation shouldBe envTwo.incarnation
    }
  }

  implicit def toMap(tags: TagSet): Map[String, String] = {
    val map = Map.newBuilder[String, String]
    tags.iterator(_.toString).foreach(pair => map += pair.key -> pair.value)
    map.result()
  }
}
