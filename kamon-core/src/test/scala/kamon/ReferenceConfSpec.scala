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
package kamon

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class ReferenceConfSpec extends WordSpec with Matchers {

  "reference.conf" should {
    "have internal-config akka.actor.provider that is akka 2.3 compatible" in {
      val conf = ConfigFactory.load("reference.conf")
      conf.getString("kamon.internal-config.akka.actor.provider") shouldEqual "akka.actor.LocalActorRefProvider"
    }
  }
}
