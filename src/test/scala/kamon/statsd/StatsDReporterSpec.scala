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

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.statsd.StatsDServer.Metric
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}
import kamon.statsd.StatsDReporterSpec._
import org.scalatest.OptionValues._

class StatsDReporterSpec extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {

  val statsDServer = new StatsDServer()
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
  val metricKeyGenerator = new SimpleMetricKeyGenerator(config)
  val testConfig: Config = ConfigFactory.load(config).withFallback(ConfigFactory.load())

  "the StatsDReporterSpec" should {

    "flush the metrics data it receives" in  {
      val name = generateMetricName()
      Kamon.gauge(name).increment()

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "1.0", Gauge, None))
    }

    "flush the metrics data for each unique value it receives" in  {
      val nameOne = generateMetricName()
      val nameTwo = generateMetricName()
      Kamon.gauge(nameOne).increment(2)
      Kamon.counter(nameTwo).increment(3)

      val packet = statsDServer.getPacket(packet => List(nameOne, nameTwo).forall(name => packet.hasMetric(_.name == name.asMetricName)))
      val metricOne = packet.getMetric(_.name == nameOne.asMetricName)
      metricOne.value should be (Metric(nameOne.asMetricName, "2.0", Gauge, None))
      val metricTwo = packet.getMetric(_.name == nameTwo.asMetricName)
      metricTwo.value should be (Metric(nameTwo.asMetricName, "3.0", Counter, None))
    }

  }

  private implicit class StringToMetricName(name: String) {
    def asMetricName: String = {
      metricKeyGenerator.generateKey(name, Map.empty)
    }
  }

  override def beforeAll(): Unit = {
    statsDServer.start()
    Kamon.addReporter(new StatsDReporter())
  }

  before {
    Kamon.reconfigure(testConfig)
    statsDServer.clear()
  }

  override def afterAll(): Unit = {
    Kamon.stopAllReporters()
    statsDServer.stop()
  }

}

object StatsDReporterSpec {

  val Application = "kamon-test"
  val HostnameOverride = "kamon-host-test"
  val Gauge = "g"
  val Counter = "c"

  val metricNameCounter = new AtomicInteger()
  def generateMetricName(): String = "metric-" + metricNameCounter.incrementAndGet()

}