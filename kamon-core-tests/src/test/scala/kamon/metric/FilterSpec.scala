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
