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

import kamon.context.Storage.Scope
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class ThreadLocalStorageSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

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

  "the Storage.CrossThreadLocal implementation of Context storage" should {
    "return a empty context when no context has been set" in {
      CrossTLS.current() shouldBe Context.Empty
    }

    "return the empty value for keys that have not been set in the context" in {
      CrossTLS.current().get(TestKey) shouldBe 42
      CrossTLS.current().get(AnotherKey) shouldBe 99
      CrossTLS.current().get(BroadcastKey) shouldBe "i travel around"

      ScopeWithKey.get(TestKey) shouldBe 43
      ScopeWithKey.get(AnotherKey) shouldBe 99
      ScopeWithKey.get(BroadcastKey) shouldBe "i travel around"
    }

    "allow setting a context as current and remove it when closing the Scope" in {
      CrossTLS.current() shouldBe Context.Empty

      val scope = CrossTLS.store(ScopeWithKey)
      CrossTLS.current() shouldBe theSameInstanceAs(ScopeWithKey)
      scope.close()

      CrossTLS.current() shouldBe Context.Empty
    }

    "Allow closing the scope in a different thread than the original" in {
      var scope: Scope = null

      val f1 = Future {
        // previous context
        CrossTLS.store(ContextWithAnotherKey)
        scope = CrossTLS.store(ScopeWithKey)
        Thread.sleep(10)
        CrossTLS.current() shouldBe theSameInstanceAs(ScopeWithKey)
      }(ec)

      val f2 = Future {
        while (scope == null) {} // wait for scope to be created in the other thread
        CrossTLS.current() shouldBe Context.Empty
        scope.close()
        CrossTLS.current() shouldBe theSameInstanceAs(ContextWithAnotherKey)
      }(ec)

      f1.flatMap(_ => f2)(ec).futureValue
    }

  }

  override protected def afterAll(): Unit = {
    ec.shutdown()
    super.afterAll()
  }

  val TLS: Storage = Storage.ThreadLocal()
  val CrossTLS: Storage = Storage.CrossThreadLocal()
  val TestKey = Context.key("test-key", 42)
  val AnotherKey = Context.key("another-key", 99)
  val BroadcastKey = Context.key("broadcast", "i travel around")
  val ScopeWithKey = Context.of(TestKey, 43)
  val ContextWithAnotherKey = Context.of(AnotherKey, 98)
}
