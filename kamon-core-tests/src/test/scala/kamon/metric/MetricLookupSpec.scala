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

import java.util.Collections.{singletonMap => javaMap}

import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}

class MetricLookupSpec extends WordSpec with Matchers {

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

      "always return the same min-max-counter metric" in {
        val minMaxCounterOne = Kamon.minMaxCounter("min-max-counter-lookup")
        val minMaxCounterTwo = Kamon.minMaxCounter("min-max-counter-lookup")
        minMaxCounterOne shouldBe theSameInstanceAs(minMaxCounterTwo)
      }
    }

    "refine a metric with tags and" should {
      "always return the same histogram for a set of tags" in {
        val histogramOne = Kamon.histogram("histogram-lookup").refine("tag" -> "value")
        val histogramTwo = Kamon.histogram("histogram-lookup").refine("tag" -> "value")
        histogramOne shouldBe theSameInstanceAs(histogramTwo)
      }

      "always return the same counter for a set of tags" in {
        val counterOne = Kamon.counter("counter-lookup").refine("tag" -> "value")
        val counterTwo = Kamon.counter("counter-lookup").refine("tag" -> "value")
        counterOne shouldBe theSameInstanceAs(counterTwo)
      }

      "always return the same gauge for a set of tags" in {
        val gaugeOne = Kamon.gauge("gauge-lookup").refine("tag" -> "value")
        val gaugeTwo = Kamon.gauge("gauge-lookup").refine("tag" -> "value")
        gaugeOne shouldBe theSameInstanceAs(gaugeTwo)
      }

      "always return the same min-max-counter for a set of tags" in {
        val minMaxCounterOne = Kamon.minMaxCounter("min-max-counter-lookup").refine("tag" -> "value")
        val minMaxCounterTwo = Kamon.minMaxCounter("min-max-counter-lookup").refine("tag" -> "value")
        minMaxCounterOne shouldBe theSameInstanceAs(minMaxCounterTwo)
      }
    }

    "refine a metric with java tags and" should {
      "always return the same histogram for a set of tags" in {
        val histogramOne = Kamon.histogram("histogram-lookup").refine(javaMap("tag", "value"))
        val histogramTwo = Kamon.histogram("histogram-lookup").refine(javaMap("tag", "value"))
        histogramOne shouldBe theSameInstanceAs(histogramTwo)
      }

      "always return the same counter for a set of tags" in {
        val counterOne = Kamon.counter("counter-lookup").refine(javaMap("tag", "value"))
        val counterTwo = Kamon.counter("counter-lookup").refine(javaMap("tag", "value"))
        counterOne shouldBe theSameInstanceAs(counterTwo)
      }

      "always return the same gauge for a set of tags" in {
        val gaugeOne = Kamon.gauge("gauge-lookup").refine(javaMap("tag", "value"))
        val gaugeTwo = Kamon.gauge("gauge-lookup").refine(javaMap("tag", "value"))
        gaugeOne shouldBe theSameInstanceAs(gaugeTwo)
      }

      "always return the same min-max-counter for a set of tags" in {
        val minMaxCounterOne = Kamon.minMaxCounter("min-max-counter-lookup").refine(javaMap("tag", "value"))
        val minMaxCounterTwo = Kamon.minMaxCounter("min-max-counter-lookup").refine(javaMap("tag", "value"))
        minMaxCounterOne shouldBe theSameInstanceAs(minMaxCounterTwo)
      }
    }
  }

}
