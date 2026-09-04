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
import kamon.tag.TagSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SimpleMetricKeyGeneratorSpec extends AnyWordSpec with Matchers {

  val defaultConfiguration: Config = ConfigFactory.parseString(
    """
      |kamon {
      |  environment {
      |    service = "kamon"
      |    host = "auto"
      |  }
      |  statsd.simple-metric-key-generator {
      |    include-hostname = true
      |    include-environment-tags = true
      |    metric-name-normalization-strategy = normalize
      |  }
      |}
    """.stripMargin
  ).withFallback(ConfigFactory.load())

  val environmentTagsConfiguration: Config = ConfigFactory.parseString(
    """
      |kamon {
      |  environment {
      |    tags {
      |      tag-3 = "value-3"
      |      tag-4 = "value-4"
      |    }
      |  }
      |}
    """.stripMargin
  ).withFallback(defaultConfiguration)

  "the SimpleMetricKeyGenerator" should {

    "generate metric names that follow the application.host.entity.[tagName.tagValue]* pattern by default" in {
      Kamon.reconfigure(defaultConfiguration)
      val generator = new SimpleMetricKeyGenerator(defaultConfiguration.getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey(
        "actor",
        TagSet.from(Map("metric-name-1" -> "/user/example", "metric-name-2" -> "processing-time"))
      ) should be(s"kamon.$host.actor.metric-name-1._user_example.metric-name-2.processing-time")
      generator.generateKey(
        "trace",
        TagSet.from(Map("metric-name-1" -> "POST: /kamon/example", "metric-name-2" -> "elapsed-time"))
      ) should be(s"kamon.$host.trace.metric-name-1.POST-_kamon_example.metric-name-2.elapsed-time")
    }

    "generate metric names with tags sorted by tag name" in {
      Kamon.reconfigure(defaultConfiguration)
      val generator = new SimpleMetricKeyGenerator(defaultConfiguration.getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey("actor", TagSet.from(Map("tag-1" -> "value-1", "tag-2" -> "value-2"))) should be(
        s"kamon.$host.actor.tag-1.value-1.tag-2.value-2"
      )
      generator.generateKey("actor", TagSet.from(Map("tag-2" -> "value-2", "tag-1" -> "value-1"))) should be(
        s"kamon.$host.actor.tag-1.value-1.tag-2.value-2"
      )
    }

    "generate metric names with environment tags sorted by tag name" in {
      Kamon.reconfigure(environmentTagsConfiguration)
      val generator = new SimpleMetricKeyGenerator(defaultConfiguration.getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey("actor", TagSet.from(Map("tag-1" -> "value-1", "tag-2" -> "value-2"))) should be(
        s"kamon.$host.actor.tag-1.value-1.tag-2.value-2.tag-3.value-3.tag-4.value-4"
      )
      generator.generateKey("actor", TagSet.from(Map("tag-2" -> "value-2", "tag-1" -> "value-1"))) should be(
        s"kamon.$host.actor.tag-1.value-1.tag-2.value-2.tag-3.value-3.tag-4.value-4"
      )
    }

    "generate metric names without tags that follow the application.host.entity.entity-name.metric-name pattern by default" in {
      Kamon.reconfigure(defaultConfiguration)
      val generator = new SimpleMetricKeyGenerator(defaultConfiguration.getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey("actor", TagSet.Empty) should be(s"kamon.$host.actor")
    }

    "removes host name when attribute 'include-hostname' is set to false" in {
      Kamon.reconfigure(defaultConfiguration)
      val hostOverrideConfig =
        ConfigFactory.parseString("kamon.statsd.simple-metric-key-generator.include-hostname = false")
      val generator =
        new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration).getConfig("kamon.statsd"))

      generator.generateKey(
        "actor",
        TagSet.from(Map("metric-name-1" -> "/user/example", "metric-name-2" -> "processing-time"))
      ) should be("kamon.actor.metric-name-1._user_example.metric-name-2.processing-time")
      generator.generateKey(
        "trace",
        TagSet.from(Map("metric-name-1" -> "POST: /kamon/example", "metric-name-2" -> "elapsed-time"))
      ) should be("kamon.trace.metric-name-1.POST-_kamon_example.metric-name-2.elapsed-time")
    }

    "remove spaces, colons and replace '/' with '_' when the normalization strategy is 'normalize'" in {
      Kamon.reconfigure(defaultConfiguration)
      val hostOverrideConfig = ConfigFactory.parseString(
        "kamon.statsd.simple-metric-key-generator.metric-name-normalization-strategy = normalize"
      )
      val generator =
        new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration).getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey(
        "actor",
        TagSet.from(Map("metric-name-1" -> "/user/example", "metric-name-2" -> "processing-time"))
      ) should be(s"kamon.$host.actor.metric-name-1._user_example.metric-name-2.processing-time")
      generator.generateKey(
        "trace",
        TagSet.from(Map("metric-name-1" -> "POST: /kamon/example", "metric-name-2" -> "elapsed-time"))
      ) should be(s"kamon.$host.trace.metric-name-1.POST-_kamon_example.metric-name-2.elapsed-time")
    }

    "percent-encode special characters in the group name and hostname when the normalization strategy is 'normalize'" in {
      Kamon.reconfigure(defaultConfiguration)
      val hostOverrideConfig = ConfigFactory.parseString(
        "kamon.statsd.simple-metric-key-generator.metric-name-normalization-strategy = percent-encode"
      )
      val generator =
        new SimpleMetricKeyGenerator(hostOverrideConfig.withFallback(defaultConfiguration).getConfig("kamon.statsd"))
      val host = generator.normalizer(Kamon.environment.host)

      generator.generateKey(
        "actor",
        TagSet.from(Map("metric-name-1" -> "/user/example", "metric-name-2" -> "processing-time"))
      ) should be(s"kamon.$host.actor.metric-name-1.%2Fuser%2Fexample.metric-name-2.processing-time")
      generator.generateKey(
        "trace",
        TagSet.from(Map("metric-name-1" -> "POST: /kamon/example", "metric-name-2" -> "elapsed-time"))
      ) should be(s"kamon.$host.trace.metric-name-1.POST%3A%20%2Fkamon%2Fexample.metric-name-2.elapsed-time")
    }
  }

}
