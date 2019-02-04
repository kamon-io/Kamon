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

package kamon
package module

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.testkit.Reconfigure
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import kamon.{MetricReporter => LegacyMetricReporter}

class ModuleRegistrySpec extends WordSpec with Matchers with Reconfigure with Eventually with BeforeAndAfterAll  {
  "The ModuleRegistry" when {
    "working with metrics reporters" should {
      "report all metrics if no filters are applied" in {
        Kamon.counter("test.hello").increment()
        Kamon.counter("test.world").increment()
        Kamon.counter("other.hello").increment()

        val reporter = new SeenMetricsReporter()
        val subscription = Kamon.addReporter(reporter, "reporter-registry-spec")

        eventually {
          reporter.snapshotCount() should be >= 1
          reporter.metrics() should contain allOf(
            "test.hello",
            "test.world",
            "other.hello"
          )
        }

        subscription.cancel()
      }

      "default to deny all metrics if a provided filter name doesn't exist" in {
        Kamon.counter("test.hello").increment()
        Kamon.counter("test.world").increment()
        Kamon.counter("other.hello").increment()

        val reporter = new SeenMetricsReporter()
        val subscription = Kamon.addReporter(reporter, "reporter-registry-spec", "does-not-exist")

        eventually {
          reporter.snapshotCount() should be >= 1
          reporter.metrics() shouldBe empty
        }

        subscription.cancel()
      }

      "apply existent filters" in {
        Kamon.counter("test.hello").increment()
        Kamon.counter("test.world").increment()
        Kamon.counter("other.hello").increment()

        val reporter = new SeenMetricsReporter()
        val subscription = Kamon.addReporter(reporter, "reporter-registry-spec", "real-filter")

        eventually {
          reporter.snapshotCount() should be >= 1
          reporter.metrics() should contain allOf(
            "test.hello",
            "test.world"
          )
        }

        subscription.cancel()
      }
    }
  }


  override protected def beforeAll(): Unit = {
    applyConfig(
      """
        |kamon {
        |  metric.tick-interval = 10 millis
        |
        |  util.filters {
        |    real-filter {
        |      includes = [ "test**" ]
        |    }
        |  }
        |}
        |
    """.stripMargin
    )
  }


  override protected def afterAll(): Unit = {
    resetConfig()
  }

  class SeenMetricsReporter extends LegacyMetricReporter {
    @volatile private var count = 0
    @volatile private var seenMetrics = Seq.empty[String]

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      import snapshot.metrics._
      count += 1
      seenMetrics = counters.map(_.name) ++ histograms.map(_.name) ++ gauges.map(_.name) ++ rangeSamplers.map(_.name)
    }

    def metrics(): Seq[String] =
      seenMetrics

    def snapshotCount(): Int =
      count

    override def start(): Unit = {}
    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
  }
}
