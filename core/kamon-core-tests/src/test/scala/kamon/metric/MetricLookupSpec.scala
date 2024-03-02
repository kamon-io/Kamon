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

package kamon.metric

import kamon.Kamon
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Collections.{singletonMap => javaMap}

class MetricLookupSpec extends AnyWordSpec with Matchers {

  "the Kamon companion object" can {
    "lookup a metric and" should {
      "always return the same histogram metric" in {
        val histogramOne = Kamon.histogram("histogram-lookup")
        val histogramTwo = Kamon.histogram("histogram-lookup")
        histogramOne shouldBe theSameInstanceAs(histogramTwo)
      }

      "always return the same counter metric" in {
        val counterOne = Kamon.counter("counter-lookup")
        val counterTwo = Kamon.counter("counter-lookup")
        counterOne shouldBe theSameInstanceAs(counterTwo)
      }

      "always return the same gauge metric" in {
        val gaugeOne = Kamon.gauge("gauge-lookup")
        val gaugeTwo = Kamon.gauge("gauge-lookup")
        gaugeOne shouldBe theSameInstanceAs(gaugeTwo)
      }

      "always return the same range sampler metric" in {
        val rangeSamplerOne = Kamon.rangeSampler("range-sampler-lookup")
        val rangeSamplerTwo = Kamon.rangeSampler("range-sampler-lookup")
        rangeSamplerOne shouldBe theSameInstanceAs(rangeSamplerTwo)
      }

      "throw an IllegalArgumentException when a metric redefinition is attempted" in {
        the[IllegalArgumentException] thrownBy {
          Kamon.counter("original-counter")
          Kamon.gauge("original-counter")

        } should have message (redefinitionError("original-counter", "counter", "gauge"))

        the[IllegalArgumentException] thrownBy {
          Kamon.counter("original-counter")
          Kamon.histogram("original-counter")

        } should have message (redefinitionError("original-counter", "counter", "histogram"))

        the[IllegalArgumentException] thrownBy {
          Kamon.counter("original-counter")
          Kamon.rangeSampler("original-counter")

        } should have message (redefinitionError("original-counter", "counter", "rangeSampler"))

        the[IllegalArgumentException] thrownBy {
          Kamon.counter("original-counter")
          Kamon.timer("original-counter")

        } should have message (redefinitionError("original-counter", "counter", "timer"))

        the[IllegalArgumentException] thrownBy {
          Kamon.histogram("original-histogram")
          Kamon.counter("original-histogram")

        } should have message (redefinitionError("original-histogram", "histogram", "counter"))
      }
    }

    "refine a metric with tags and" should {
      "always return the same histogram for a set of tags" in {
        val histogramOne = Kamon.histogram("histogram-lookup").withTag("tag", "value")
        val histogramTwo = Kamon.histogram("histogram-lookup").withTag("tag", "value")
        val histogramThree = Kamon.histogram("histogram-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

        histogramOne shouldBe theSameInstanceAs(histogramTwo)
        histogramOne shouldBe theSameInstanceAs(histogramThree)
      }

      "always return the same counter for a set of tags" in {
        val counterOne = Kamon.counter("counter-lookup").withTag("tag", "value")
        val counterTwo = Kamon.counter("counter-lookup").withTag("tag", "value")
        val counterThree = Kamon.counter("counter-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

        counterOne shouldBe theSameInstanceAs(counterTwo)
        counterOne shouldBe theSameInstanceAs(counterThree)
      }

      "always return the same gauge for a set of tags" in {
        val gaugeOne = Kamon.gauge("gauge-lookup").withTag("tag", "value")
        val gaugeTwo = Kamon.gauge("gauge-lookup").withTag("tag", "value")
        val gaugeThree = Kamon.gauge("gauge-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

        gaugeOne shouldBe theSameInstanceAs(gaugeTwo)
        gaugeOne shouldBe theSameInstanceAs(gaugeThree)
      }

      "always return the same range-sampler for a set of tags" in {
        val rangeSamplerOne = Kamon.rangeSampler("range-sampler-lookup").withTag("tag", "value")
        val rangeSamplerTwo = Kamon.rangeSampler("range-sampler-lookup").withTag("tag", "value")
        val rangeSamplerThree =
          Kamon.rangeSampler("range-sampler-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

        rangeSamplerOne shouldBe theSameInstanceAs(rangeSamplerTwo)
        rangeSamplerOne shouldBe theSameInstanceAs(rangeSamplerThree)
      }
    }
  }

  def redefinitionError(name: String, currentType: String, newType: String): String =
    s"Cannot redefine metric [$name] as a [$newType], it was already registered as a [$currentType]"
}
