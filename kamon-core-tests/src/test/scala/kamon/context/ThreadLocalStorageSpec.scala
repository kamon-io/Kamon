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
  val TestKey = Key.local("test-key", 42)
  val AnotherKey = Key.local("another-key", 99)
  val BroadcastKey = Key.broadcast("broadcast", "i travel around")
  val ScopeWithKey = Context.create().withKey(TestKey, 43)
}
