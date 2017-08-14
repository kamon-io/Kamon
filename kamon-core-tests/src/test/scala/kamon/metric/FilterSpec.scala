package kamon
package metric

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}


class FilterSpec extends WordSpec with Matchers {
  val testConfig = ConfigFactory.parseString(
    """
      |kamon.util.filters {
      |
      |  some-filter {
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
      |
      |  "filter.with.quotes" {
      |    includes = ["**"]
      |    excludes = ["not-me"]
      |  }
      |}
    """.stripMargin
  )

  Kamon.reconfigure(testConfig.withFallback(Kamon.config()))

  "the entity filters" should {
    "reject anything that doesn't match any configured filter" in {
      Kamon.filter("not-a-filter", "hello") shouldBe false
    }

    "evaluate patterns for filters with includes and excludes" in {
      Kamon.filter("some-filter", "anything") shouldBe true
      Kamon.filter("some-filter", "some-other") shouldBe true
      Kamon.filter("some-filter", "not-me") shouldBe false
    }

    "allow configuring includes only or excludes only for any filter" in {
      Kamon.filter("only-includes", "only-me") shouldBe true
      Kamon.filter("only-includes", "anything") shouldBe false
      Kamon.filter("only-excludes", "any-other") shouldBe false
      Kamon.filter("only-excludes", "not-me") shouldBe false
    }

    "allow to explicitly decide whether patterns are treated as Glob or Regex" in {
      Kamon.filter("specific-rules", "/user/accepted") shouldBe true
      Kamon.filter("specific-rules", "/other/rejected/") shouldBe false
      Kamon.filter("specific-rules", "test-5") shouldBe true
      Kamon.filter("specific-rules", "test-6") shouldBe false
    }

    "allow filters with quoted names" in {
      Kamon.filter("filter.with.quotes", "anything") shouldBe true
      Kamon.filter("filter.with.quotes", "some-other") shouldBe true
      Kamon.filter("filter.with.quotes", "not-me") shouldBe false
    }

  }
}
