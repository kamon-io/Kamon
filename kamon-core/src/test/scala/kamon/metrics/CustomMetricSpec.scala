/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metrics

import akka.testkit.TestKitBase
import org.scalatest.{ Matchers, WordSpecLike }
import akka.actor.ActorSystem
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.metrics.MetricSnapshot.Measurement

class CustomMetricSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  filters = [
      |    {
      |      custom-metric {
      |        includes = [ "test/*" ]
      |        excludes = [ ]
      |      }
      |    }
      |  ]
      |}
    """.stripMargin))

  "the Kamon custom metrics support" should {
    "allow registering a custom metric with the Metrics extension" in {
      val recorder = Kamon(Metrics).register(CustomMetric("test/sample-counter"), CustomMetric.histogram(100, 2, Scale.Unit))

      recorder should be('defined)
    }

    "allow subscriptions to custom metrics using the default subscription protocol" in {
      val recorder = Kamon(Metrics).register(CustomMetric("test/sample-counter"), CustomMetric.histogram(100, 2, Scale.Unit))

      recorder.map { r ⇒
        r.record(100)
        r.record(15)
        r.record(0)
        r.record(50)
      }

      Kamon(Metrics).subscribe(CustomMetric, "test/sample-counter", testActor)

      val recordedValues = within(5 seconds) {
        val snapshot = expectMsgType[TickMetricSnapshot]
        snapshot.metrics(CustomMetric("test/sample-counter")).metrics(CustomMetric.RecordedValues)
      }

      recordedValues.min should equal(0)
      recordedValues.max should equal(100)
      recordedValues.numberOfMeasurements should equal(4)
      recordedValues.measurements should contain allOf (
        Measurement(0, 1),
        Measurement(15, 1),
        Measurement(50, 1),
        Measurement(100, 1))
    }
  }

}
