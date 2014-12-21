/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import org.scalatest.{Matchers, WordSpecLike}

class GlobPathFilterSpec extends WordSpecLike with Matchers {
  "The GlobPathFilter" should {

    "match a single expression" in {
      val filter = GlobPathFilter("/user/actor")

      filter.accept("/user/actor") should be (true)

      filter.accept("/user/actor/something") should be (false)
      filter.accept("/user/actor/somethingElse") should be (false)
    }

    "match all expressions in the same level" in {
      val filter = GlobPathFilter("/user/*")

      filter.accept("/user/actor") should be (true)
      filter.accept("/user/otherActor") should be (true)

      filter.accept("/user/something/actor") should be (false)
      filter.accept("/user/something/otherActor") should be (false)
    }
  }
}
