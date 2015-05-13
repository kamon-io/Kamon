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

import akka.event.NoLogging
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.collection.JavaConversions._

/**
 * @since 21.04.2015
 */
class MetricsSubscriptionSpec extends WordSpecLike with Matchers {

  val instance = new MetricsSubscription {
    override def log = NoLogging
  }

  val metrics = Seq("user-metrics", "trace", "akka-dispatcher", "akka-actor").zipWithIndex
  val metricsStr = metrics map { m ⇒ m._1 + " = \"" + "*" * (m._2 + 1) + "\"" } mkString "\n"
  val fullConfig = ConfigFactory.parseString(s"kamon.newrelic.subscriptions { $metricsStr }")

   "the MetricsSubscription" should {

    "read correct subscriptions from full configuration" in {
      val cfg = instance.subscriptions(fullConfig)
      cfg.entrySet().size should be(4)
      cfg.entrySet().foreach { metric ⇒
        val idx = metrics.indexWhere(_._1 == metric.getKey)
        metric.getValue.unwrapped().toString should be("*" * (idx + 1))
      }
    }
  }
}
