package kamon.instrumentation.caffeine

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Waiters.timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt

class CaffeineSyncCacheSpec
    extends AnyWordSpec
    with Matchers
    with InitAndStopKamonAfterAll
    with TestSpanReporter {
  "Caffeine instrumentation for sync caches" should {
    val cache: Cache[String, String] = Caffeine.newBuilder()
      .build[String, String]()

    "create a span when putting a value" in {
      cache.put("a", "key")
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "caffeine.put"
        testSpanReporter().spans() shouldBe empty
      }
    }

    "create a span when accessing an existing key" in {
      cache.get(
        "a",
        new java.util.function.Function[String, String] {
          override def apply(a: String): String = "value"
        }
      )
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "caffeine.computeIfAbsent"
        testSpanReporter().spans() shouldBe empty
      }
    }

    "create only one span when using putAll" in {
      val map = Map("b" -> "value", "c" -> "value").asJava
      cache.putAll(map)
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "caffeine.putAll"
        testSpanReporter().spans() shouldBe empty
        testSpanReporter().clear()
      }
    }

    "create a tagged span when accessing a key that does not exist" in {
      cache.getIfPresent("not_exists")
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "caffeine.getIfPresent"
        span.tags.all().foreach(_.key shouldBe "cache.miss")
        testSpanReporter().spans() shouldBe empty
      }
    }

    "create a span when using getAllPresent" in {
      cache.getAllPresent(Seq("a", "b").asJava)
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "caffeine.getAllPresent"
        testSpanReporter().spans() shouldBe empty
      }
    }
  }
}
