package kamon
package metric

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}


class EntityFilterSpec extends WordSpec with Matchers {
  val testConfig = ConfigFactory.parseString(
    """
      |kamon.metric.filters {
      |  accept-unmatched-categories = false
      |
      |  some-category {
      |    includes = ["**"]
      |    excludes = ["not-me"]
      |  }
      |
      |  only-includes {
      |    includes = ["only-me"]
      |  }
      |
      |  only-excludes {
      |    excludes = ["not-me"]
      |  }
      |
      |  specific-rules {
      |    includes = ["glob:/user/**", "regex:test-[0-5]"]
      |  }
      |}
    """.stripMargin
  )

  "the entity filters" should {
    "use the accept-unmatched-categories setting when there is no configuration for a given category" in {
      val acceptUnmatched = EntityFilter.fromConfig(ConfigFactory.parseString("kamon.metric.filters.accept-unmatched-categories=true"))
      val rejectUnmatched = EntityFilter.fromConfig(ConfigFactory.parseString("kamon.metric.filters.accept-unmatched-categories=false"))

      acceptUnmatched.accept(Entity("a", "b", Map.empty)) shouldBe true
      rejectUnmatched.accept(Entity("a", "b", Map.empty)) shouldBe false
    }

    "accept entities that are matched by any include and none exclude filters" in {
      val entityFilter = EntityFilter.fromConfig(testConfig)

      entityFilter.accept(Entity("anything", "anything", Map.empty)) shouldBe false
      entityFilter.accept(Entity("anything", "some-category", Map.empty)) shouldBe true
      entityFilter.accept(Entity("not-me", "some-category", Map.empty)) shouldBe false
    }

    "allow configuring includes only or excludes only for any category" in {
      val entityFilter = EntityFilter.fromConfig(testConfig)

      entityFilter.accept(Entity("only-me", "only-includes", Map.empty)) shouldBe true
      entityFilter.accept(Entity("anything", "only-includes", Map.empty)) shouldBe false
      entityFilter.accept(Entity("any-other", "only-excludes", Map.empty)) shouldBe false
      entityFilter.accept(Entity("not-me", "only-excludes", Map.empty)) shouldBe false
    }

    "allow to explicitly decide whether patterns are treated as Glob or Regex" in {
      val entityFilter = EntityFilter.fromConfig(testConfig)

      entityFilter.accept(Entity("/user/accepted", "specific-rules", Map.empty)) shouldBe true
      entityFilter.accept(Entity("/other/rejected/", "specific-rules", Map.empty)) shouldBe false
      entityFilter.accept(Entity("test-5", "specific-rules", Map.empty)) shouldBe true
      entityFilter.accept(Entity("test-6", "specific-rules", Map.empty)) shouldBe false
    }
  }
}