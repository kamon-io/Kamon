/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import kamon.metric.instrument.CollectionContext
import kamon.metric.{ DefaultEntitySnapshot, Entity }
import org.scalatest.{ Matchers, WordSpecLike }

/**
 * @since 21.04.2015
 */
class AkkaMetricExtractorSpec extends WordSpecLike with Matchers {

  val ame = AkkaMetricExtractor

  "the AkkaMetricExtractor" should {
    "have a camelize method" which {
      "is ok with an empty string" in {
        ame.camelize("") should be("")
      }
      "is ok with normal '-'" in {
        ame.camelize("akka-dispatcher-string") should be("AkkaDispatcherString")
      }
      "is ok with multiple '-'" in {
        ame.camelize("akka--dispatcher----string") should be("AkkaDispatcherString")
      }
      "is ok with starting end ending '-'" in {
        ame.camelize("--akka-dispatcher-string-") should be("AkkaDispatcherString")
      }
      "is ok with solely '-'" in {
        ame.camelize("----") should be("")
      }
      "is ok with single letters" in {
        ame.camelize("a-b-c-d-e") should be("ABCDE")
      }

    }
    "recognize akka categories" when {
      "provided right category" in {
        val kv = (Entity("name", "akka-actor"), new DefaultEntitySnapshot(null))
        ame.akkaMetrics(kv) shouldBe true
      }
      "provided wrong category" in {
        val kv = (Entity("name", "fooBar"), new DefaultEntitySnapshot(null))
        ame.akkaMetrics(kv) shouldBe false
      }
    }
    "extract akka metrics" when {
      "given empty snapshot, no metrics are expected" in {
        val snapshot = new DefaultEntitySnapshot(Map.empty)
        ame.logMetrics(ame.actorMetrics)("actorName", snapshot, "prefix").isEmpty shouldBe true
      }
      "given no metrics, no newrelic metrics are expected" in {
        val settings = AgentSettings(null, null, null, 0, null, 0, null, 0d)
        val result = ame.extract(settings, CollectionContext(0), Map.empty)
        result.isEmpty shouldBe true
      }
    }
  }
}
