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

package kamon.util

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FilterSpec extends AnyWordSpec with Matchers {
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
      Kamon.filter("kamon.util.filters.not-a-filter").accept("hello") shouldBe false
    }

    "evaluate patterns for filters with includes and excludes" in {
      val filter = Kamon.filter("kamon.util.filters.some-filter")
      filter.accept("anything") shouldBe true
      filter.accept("some-other") shouldBe true
      filter.accept("not-me") shouldBe false
    }

    "allow configuring includes only or excludes only for any filter" in {
      val filter = Kamon.filter("kamon.util.filters.only-includes")
      filter.accept("only-me") shouldBe true
      filter.accept("anything") shouldBe false
      filter.accept("any-other") shouldBe false
      filter.accept("not-me") shouldBe false
    }

    "allow to explicitly decide whether patterns are treated as Glob or Regex" in {
      val filter = Kamon.filter("kamon.util.filters.specific-rules")
      filter.accept("/user/accepted") shouldBe true
      filter.accept("/other/rejected/") shouldBe false
      filter.accept("test-5") shouldBe true
      filter.accept("test-6") shouldBe false
    }

    "allow filters with quoted names" in {
      val filter = Kamon.filter("kamon.util.filters.\"filter.with.quotes\"")
      filter.accept("anything") shouldBe true
      filter.accept("some-other") shouldBe true
      filter.accept("not-me") shouldBe false
    }

  }
}
