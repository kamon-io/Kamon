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
import kamon.metric.{MeasurementUnit, PeriodSnapshot}
import kamon.statsd.StatsDReporterSpec._
import kamon.statsd.StatsDServer.Metric
import org.scalatest.OptionValues._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

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
        |    tick-interval = $Interval
        |  }
        |}
        |
      """.stripMargin
    )
  val metricKeyGenerator = new SimpleMetricKeyGenerator(config)
  val testConfig: Config = ConfigFactory.load(config).withFallback(ConfigFactory.load())
  val statsDReporter = new TestStatsDReporter()

  "the StatsDReporterSpec" should {

    "flush the gauge metric data it receives" in  {
      val name = generateMetricName()
      statsDReporter.waitForNextSnapshot()
      Kamon.gauge(name).increment()

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "1.0", Gauge, None))
    }

    "flush the counter metric data it receives" in  {
      val name = generateMetricName()
      statsDReporter.waitForNextSnapshot()
      Kamon.counter(name).increment(3)

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "3.0", Counter, None))
    }

    "flush the histogram metric data it receives" in  {
      val name = generateMetricName()
      statsDReporter.waitForNextSnapshot()
      Kamon.histogram(name).record(2)

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "2.0", Timer, None))
    }

    "convert time metric in seconds before flushing it" in  {
      val name = generateMetricName()
      statsDReporter.waitForNextSnapshot()
      Kamon.histogram(name, MeasurementUnit.time.milliseconds).record(1000)

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "1.0", Timer, None))
    }

    "convert information metric in byte before flushing it" in  {
      val name = generateMetricName()
      statsDReporter.waitForNextSnapshot()
      Kamon.histogram(name, MeasurementUnit.information.kilobytes).record(1)

      val packet = statsDServer.getPacket(_.metrics.exists(_.name.contains(name)))
      val metric = packet.getMetric(_.name == name.asMetricName)
      metric.value should be (Metric(name.asMetricName, "1024.0", Timer, None))
    }

  }

  private implicit class StringToMetricName(name: String) {
    def asMetricName: String = {
      metricKeyGenerator.generateKey(name, Map.empty)
    }
  }

  override def beforeAll(): Unit = {
    statsDServer.start()
    Kamon.reconfigure(testConfig)
    Kamon.gauge(generateMetricName()).increment(1)
    Kamon.addReporter(statsDReporter)
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
  val Timer = "ms"
  val Interval: FiniteDuration = 50.millis

  val metricNameCounter = new AtomicInteger()
  def generateMetricName(): String = "metric-" + metricNameCounter.incrementAndGet()

  class TestStatsDReporter extends StatsDReporter {

    private var promise: Option[Promise[Unit]] = None

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      promise.foreach(_.trySuccess(()))
      super.reportPeriodSnapshot(snapshot)
    }

    def waitForNextSnapshot(): Unit = {
      promise = Some(Promise())
      Await.ready(promise.get.future, Interval * 10)
    }

  }

}