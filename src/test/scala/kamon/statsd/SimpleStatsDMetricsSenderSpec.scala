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
import kamon.statsd.StatsDServer.Metric
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

class SimpleStatsDMetricsSenderSpec extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {

  val statsDServer = new StatsDServer()

  val Application = "kamon-test"
  val HostnameOverride = "kamon-host-test"
  val Gauge = "g"

  val config: Config = ConfigFactory.parseString(
      s"""
        |kamon {
        |  statsd {
        |    hostname = "127.0.0.1"
        |    port = ${statsDServer.port}
        |    simple-metric-key-generator {
        |      application = $Application
        |      hostname-override = $HostnameOverride
        |      include-hostname = true
        |      metric-name-normalization-strategy = normalize
        |    }
        |  }
        |  metric {
        |    tick-interval = 1 second
        |  }
        |}
        |
      """.stripMargin
    )

  "the SimpleStatsDMetricSender" should {
    "flush the metrics data for each unique value it receives" in  {
      val testConfig = ConfigFactory.load(config).withFallback(ConfigFactory.load())
      Kamon.reconfigure(testConfig)
      Kamon.addReporter(new StatsDReporter())

      for(_ <- 1 to 10000) {
        Kamon.gauge("metric-one").increment()
      }

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains("metric-one")))
      packet.metrics should have size 1
      val metric = packet.metrics.head
      val metricName = new SimpleMetricKeyGenerator(config).generateKey("metric-one", Map())
      metric should be (Metric(metricName, "10000.0", Gauge, None))
    }
    
  }

  override def beforeAll(): Unit = {
    statsDServer.start()
  }

  before {
    statsDServer.clear()
  }

  override def afterAll(): Unit = {
    statsDServer.stop()
  }

}