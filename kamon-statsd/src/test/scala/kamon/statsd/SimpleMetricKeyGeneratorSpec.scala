/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

import com.typesafe.config.ConfigFactory
import kamon.metric.instrument.UnitOfMeasurement
import kamon.metric._
import org.scalatest.{ Matchers, WordSpec }

class SimpleMetricKeyGeneratorSpec extends WordSpec with Matchers {

  val defaultConfiguration = ConfigFactory.parseString(
    """
      |kamon.statsd.simple-metric-key-generator {
      |  application = kamon
      |  hostname-override = none
      |  include-hostname = true
      |  metric-name-normalization-strategy = normalize
      |}
    """.stripMargin)

  "the SimpleMetricKeyGenerator" should {
    "generate metric names that follow the application.host.entity.entity-name.metric-name pattern by default" in {
      implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(defaultConfiguration) {
        override def hostName: String = "localhost"
      }

      buildMetricKey("actor", "/user/example", "processing-time") should be("kamon.localhost.actor._user_example.processing-time")
      buildMetricKey("trace", "POST: /kamon/example", "elapsed-time") should be("kamon.localhost.trace.POST-_kamon_example.elapsed-time")
    }

    "allow to override the hostname" in {
      val hostOverrideConfig = ConfigFactory.parseString("kamon.statsd.simple-metric-key-generator.hostname-override = kamon-host")
      implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration)) {
        override def hostName: String = "localhost"
      }

      buildMetricKey("actor", "/user/example", "processing-time") should be("kamon.kamon-host.actor._user_example.processing-time")
      buildMetricKey("trace", "POST: /kamon/example", "elapsed-time") should be("kamon.kamon-host.trace.POST-_kamon_example.elapsed-time")
    }

    "removes host name when attribute 'include-hostname' is set to false" in {
      val hostOverrideConfig = ConfigFactory.parseString("kamon.statsd.simple-metric-key-generator.include-hostname = false")
      implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration)) {
        override def hostName: String = "localhost"
      }

      buildMetricKey("actor", "/user/example", "processing-time") should be("kamon.actor._user_example.processing-time")
      buildMetricKey("trace", "POST: /kamon/example", "elapsed-time") should be("kamon.trace.POST-_kamon_example.elapsed-time")
    }

    "remove spaces, colons and replace '/' with '_' when the normalization strategy is 'normalize'" in {
      val hostOverrideConfig = ConfigFactory.parseString("kamon.statsd.simple-metric-key-generator.metric-name-normalization-strategy = normalize")
      implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration)) {
        override def hostName: String = "localhost.local"
      }

      buildMetricKey("actor", "/user/example", "processing-time") should be("kamon.localhost_local.actor._user_example.processing-time")
      buildMetricKey("trace", "POST: /kamon/example", "elapsed-time") should be("kamon.localhost_local.trace.POST-_kamon_example.elapsed-time")
    }

    "percent-encode special characters in the group name and hostname when the normalization strategy is 'normalize'" in {
      val hostOverrideConfig = ConfigFactory.parseString("kamon.statsd.simple-metric-key-generator.metric-name-normalization-strategy = percent-encode")
      implicit val metricKeyGenerator = new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration)) {
        override def hostName: String = "localhost.local"
      }

      buildMetricKey("actor", "/user/example", "processing-time") should be("kamon.localhost%2Elocal.actor.%2Fuser%2Fexample.processing-time")
      buildMetricKey("trace", "POST: /kamon/example", "elapsed-time") should be("kamon.localhost%2Elocal.trace.POST%3A%20%2Fkamon%2Fexample.elapsed-time")
    }
  }

  def buildMetricKey(categoryName: String, entityName: String, metricName: String)(implicit metricKeyGenerator: SimpleMetricKeyGenerator): String = {
    val metric = HistogramKey(metricName, UnitOfMeasurement.Unknown)
    val entity = Entity(entityName, categoryName)
    metricKeyGenerator.generateKey(entity, metric)
  }
}
