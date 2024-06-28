package kamon.instrumentation.caffeine

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import kamon.testkit.TestSpanReporter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Waiters.timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletableFuture
import scala.concurrent.duration.DurationInt

class CaffeineAsyncCacheSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with TestSpanReporter {
  "Caffeine instrumentation for async caches" should {
    val cache: AsyncCache[String, String] = Caffeine.newBuilder()
      .buildAsync[String, String]()

    "not create a span when using put" in {
      cache.put("a", CompletableFuture.completedFuture("key"))
      eventually(timeout(2.seconds)) {
        testSpanReporter().spans() shouldBe empty
      }
    }

    "not create a span when using get" in {
      cache.get(
        "a",
        new java.util.function.Function[String, String] {
          override def apply(a: String): String = "value"
        }
      )
      eventually(timeout(2.seconds)) {
        testSpanReporter().spans() shouldBe empty
      }
    }

    "not create a span when using getIfPresent" in {
      cache.getIfPresent("not_exists")
      eventually(timeout(2.seconds)) {
        testSpanReporter().spans() shouldBe empty
      }
    }
  }
}
