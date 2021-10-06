/*
 * =========================================================================================
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

package kamon.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GlobPathFilterSpec extends AnyWordSpec with Matchers {
  "The GlobPathFilter" should {

    "match a single expression" in {
      val filter = Filter.Glob("/user/actor")

      filter.accept("/user/actor") shouldBe true
      filter.accept("/user/actor/something") shouldBe false
      filter.accept("/user/actor/somethingElse") shouldBe false
    }

    "match all expressions in the same level" in {
      val filter = Filter.Glob("/user/*")

      filter.accept("/user/actor") shouldBe true
      filter.accept("/user/otherActor") shouldBe true
      filter.accept("/user/something/actor") shouldBe false
      filter.accept("/user/something/otherActor") shouldBe false
    }

    "match any expressions when using double star alone (**)" in {
      val filter = Filter.Glob("**")

      filter.accept("GET: /ping") shouldBe true
      filter.accept("GET: /ping/pong") shouldBe true
      filter.accept("this-doesn't_look good but-passes") shouldBe true
    }

    "match all expressions and cross the path boundaries when using double star suffix (**)" in {
      val filter = Filter.Glob("/user/actor-**")

      filter.accept("/user/actor-") shouldBe true
      filter.accept("/user/actor-one") shouldBe true
      filter.accept("/user/actor-one/other") shouldBe true
      filter.accept("/user/something/actor") shouldBe false
      filter.accept("/user/something/otherActor") shouldBe false
    }

    "match exactly one character when using question mark (?)" in {
      val filter = Filter.Glob("/user/actor-?")

      filter.accept("/user/actor-1") shouldBe true
      filter.accept("/user/actor-2") shouldBe true
      filter.accept("/user/actor-3") shouldBe true
      filter.accept("/user/actor-one") shouldBe false
      filter.accept("/user/actor-two") shouldBe false
      filter.accept("/user/actor-tree") shouldBe false
    }
  }
}
