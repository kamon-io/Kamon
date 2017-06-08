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

package kamon.metric

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class RecorderRegistrySpec extends WordSpec with Matchers {
  private val testConfig = ConfigFactory.parseString(
    """
      |kamon.metric.filters {
      |  accept-unmatched = false
      |
      |  my-category {
      |    includes = ["**"]
      |    excludes = ["excluded"]
      |  }
      |}
    """.stripMargin
  )
  private val recorderRegistry = new RecorderRegistryImpl(testConfig.withFallback(ConfigFactory.load()))


  "the RecorderRegistry" should {
    "create entity recorders as requested and always return the same instance for a given entity" in {
      val myFirstEntityRecorder = recorderRegistry.getRecorder(Entity("my-entity", "my-category", Map.empty))
      val mySecondEntityRecorder = recorderRegistry.getRecorder(Entity("my-entity", "my-category", Map.empty))
      mySecondEntityRecorder shouldBe theSameInstanceAs(myFirstEntityRecorder)
    }

    "properly advice regarding entity filtering read from configuration" in {
      recorderRegistry.shouldTrack(Entity("my-entity", "my-category", Map.empty)) shouldBe true
      recorderRegistry.shouldTrack(Entity("other-eny", "my-category", Map.empty)) shouldBe true
      recorderRegistry.shouldTrack(Entity("excluded", "my-category", Map.empty)) shouldBe false
    }

    "allow removing entities" in {
      val myFirstEntityRecorder = recorderRegistry.getRecorder(Entity("my-entity", "my-category", Map.empty))
      recorderRegistry.removeRecorder(Entity("my-entity", "my-category", Map.empty))

      val mySecondEntityRecorder = recorderRegistry.getRecorder(Entity("my-entity", "my-category", Map.empty))
      mySecondEntityRecorder shouldNot be theSameInstanceAs(myFirstEntityRecorder)
    }
  }
}
