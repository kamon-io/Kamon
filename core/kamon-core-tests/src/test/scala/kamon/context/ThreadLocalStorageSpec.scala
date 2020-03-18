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

package kamon.context

import org.scalatest.{Matchers, WordSpec}

class ThreadLocalStorageSpec extends WordSpec with Matchers {

  "the Storage.ThreadLocal implementation of Context storage" should {
    "return a empty context when no context has been set" in {
      TLS.current() shouldBe Context.Empty
    }

    "return the empty value for keys that have not been set in the context" in {
      TLS.current().get(TestKey) shouldBe 42
      TLS.current().get(AnotherKey) shouldBe 99
      TLS.current().get(BroadcastKey) shouldBe "i travel around"

      ScopeWithKey.get(TestKey) shouldBe 43
      ScopeWithKey.get(AnotherKey) shouldBe 99
      ScopeWithKey.get(BroadcastKey) shouldBe "i travel around"
    }

    "allow setting a context as current and remove it when closing the Scope" in {
      TLS.current() shouldBe Context.Empty

      val scope = TLS.store(ScopeWithKey)
      TLS.current() shouldBe theSameInstanceAs(ScopeWithKey)
      scope.close()

      TLS.current() shouldBe Context.Empty
    }

  }

  val TLS: Storage = new Storage.ThreadLocal
  val TestKey = Context.key("test-key", 42)
  val AnotherKey = Context.key("another-key", 99)
  val BroadcastKey = Context.key("broadcast", "i travel around")
  val ScopeWithKey = Context.of(TestKey, 43)
}
