/* =========================================================================================
 * Copyright © 2017 the kamon project <http://kamon.io/>
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

package kamon.akka

import kamon.testkit.BaseKamonSpec

class ActorGroupConfigSpec extends BaseKamonSpec("actor-group-config-spec") {
  "the Kamon actor-group config" should {
    "should contain the expected group names" in {
      ActorGroupConfig.groupNames should contain allOf ("test-group", "empty-group", "tracked-group")
    }
    "track correct actor groups" in {
      ActorGroupConfig.actorShouldBeTrackedUnderGroups("/user/tracked-actor") should contain theSameElementsAs List("tracked-group")
      ActorGroupConfig.actorShouldBeTrackedUnderGroups("/user/non-tracked-actor") shouldBe empty
    }

  }
}
