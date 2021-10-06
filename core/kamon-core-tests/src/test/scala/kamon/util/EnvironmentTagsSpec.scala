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
package util

import com.typesafe.config.ConfigFactory
import kamon.status.Environment
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EnvironmentTagsSpec extends AnyWordSpec with Matchers {
  private val testEnv = Environment.from(ConfigFactory.parseString(
    """
      |kamon.environment {
      |  service = environment-spec
      |  host = my-hostname
      |  instance = my-instance-name
      |
      |  tags {
      |    env = staging
      |    region = asia-1
      |  }
      |}
    """.stripMargin
  ).withFallback(ConfigFactory.defaultReference()))

  "the EnvironmentTagBuilder" should {

    "build the tags from a configuration using the current Environment" in {
      val config = ConfigFactory.parseString(
        """
          |include-service = yes
          |include-host = yes
          |include-instance = yes
          |exclude = []
        """.stripMargin)

      val env = Kamon.environment
      val tags = EnvironmentTags.from(env, config)
      tags("service") shouldBe env.service
      tags("host") shouldBe env.host
      tags("instance") shouldBe env.instance
    }

    "build tags from a custom Environment" in {
      val config = ConfigFactory.parseString(
        """
          |include-service = yes
          |include-host = yes
          |include-instance = yes
          |exclude = []
        """.stripMargin)

      val tags = EnvironmentTags.from(testEnv, config)
      tags("service") shouldBe testEnv.service
      tags("host") shouldBe testEnv.host
      tags("instance") shouldBe testEnv.instance
      tags("env") shouldBe "staging"
      tags("region") shouldBe "asia-1"

    }

    "remove excluded tags" in {
      val config = ConfigFactory.parseString(
        """
          |include-service = yes
          |include-host = yes
          |include-instance = yes
          |exclude = [ "region" ]
        """.stripMargin)

      val tags = EnvironmentTags.from(testEnv, config)
      tags("service") shouldBe testEnv.service
      tags("host") shouldBe testEnv.host
      tags("instance") shouldBe testEnv.instance
      tags("env") shouldBe "staging"
      tags.toMap.get("region") shouldBe empty
    }

    "remove all disabled elements" in {
      val config = ConfigFactory.parseString(
        """
          |include-service = no
          |include-host = no
          |include-instance = no
          |exclude = [ "region", "env" ]
        """.stripMargin)

      val tags = EnvironmentTags.from(testEnv, config)
      tags shouldBe empty
    }
  }

  implicit def toMap(tags: TagSet): Map[String, String] = {
    val map = Map.newBuilder[String, String]
    tags.iterator(_.toString).foreach(pair => map += pair.key -> pair.value)
    map.result()
  }
}
