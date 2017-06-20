/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.statsd

import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}

class SimpleStatsDMetricsSenderSpec extends WordSpec with Matchers {

   val config: Config = ConfigFactory.parseString(
      """
        |kamon {
        |  statsd {
        |    hostname = "127.0.0.1"
        |    port = 0
        |    simple-metric-key-generator {
        |      application = kamon
        |      hostname-override = kamon-host
        |      include-hostname = true
        |      metric-name-normalization-strategy = normalize
        |    }
        |  }
        |}
        |
      """.stripMargin
    )

  "the SimpleStatsDMetricSender" should {
    "flush the metrics data for each unique value it receives" in  {
      Kamon.addReporter(new StatsDReporter())

      for(_ <- 1 to 10000) {
        Kamon.gauge("metric-one").refine("awesome-gauge" -> "1").increment()
      }

//      Thread.sleep(10000)


      //
//      val udp = setup(Map(testEntity → testRecorder.collect(collectionContext)))
//      expectUDPPacket(s"$testMetricKey1:10|ms", udp)
//      expectUDPPacket(s"$testMetricKey1:30|ms", udp)
//      expectUDPPacket(s"$testMetricKey2:20|ms", udp)
    }

//    "include the correspondent sampling rate when rendering multiple occurrences of the same value" in new SimpleSenderFixture {
//      val testMetricKey = buildMetricKey(testEntity, "metric-one")
//      val testRecorder = buildRecorder("user/kamon")
//      testRecorder.metricOne.record(10L)
//      testRecorder.metricOne.record(10L)
//
//      val udp = setup(Map(testEntity → testRecorder.collect(collectionContext)))
//      expectUDPPacket(s"$testMetricKey:10|ms|@0.5", udp)
//    }
  }
}