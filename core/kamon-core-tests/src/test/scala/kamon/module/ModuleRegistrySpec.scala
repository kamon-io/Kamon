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

import java.util.concurrent.atomic.AtomicLong
import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.testkit.Reconfigure
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class ModuleRegistrySpec extends AnyWordSpec with Matchers with Reconfigure with Eventually with BeforeAndAfterAll {
  "The ModuleRegistry" when {
    "working with metrics reporters" should {
      "report all metrics if no filters are applied" in {
        Kamon.counter("test.hello").withoutTags().increment()
        Kamon.counter("test.world").withoutTags().increment()
        Kamon.counter("other.hello").withoutTags().increment()

        val reporter = new SeenMetricsReporter()
        val subscription = Kamon.registerModule("reporter-registry-spec", reporter)

        eventually {
          reporter.snapshotCount() should be >= 1
          reporter.metrics() should contain allOf (
            "test.hello",
            "test.world",
            "other.hello"
          )
        }

        subscription.cancel()
      }

      "default to deny all metrics if a provided filter name doesn't exist" in {
        Kamon.counter("test.hello").withoutTags().increment()
        Kamon.counter("test.world").withoutTags().increment()
        Kamon.counter("other.hello").withoutTags().increment()

        val originalReporter = new SeenMetricsReporter()
        val reporter =
          MetricReporter.withTransformations(originalReporter, MetricReporter.filterMetrics("does-not-exist"))
        val subscription = Kamon.registerModule("reporter-registry-spec", reporter)

        eventually {
          originalReporter.snapshotCount() should be >= 1
          originalReporter.metrics() shouldBe empty
        }

        subscription.cancel()
      }

      "apply existent filters" in {
        Kamon.counter("test.hello").withoutTags().increment()
        Kamon.counter("test.world").withoutTags().increment()
        Kamon.counter("other.hello").withoutTags().increment()

        val originalReporter = new SeenMetricsReporter()
        val reporter =
          MetricReporter.withTransformations(originalReporter, MetricReporter.filterMetrics("test-metric-filter"))
        val subscription = Kamon.registerModule("reporter-registry-spec", reporter)

        eventually {
          originalReporter.snapshotCount() should be >= 1
          originalReporter.metrics() should contain allOf (
            "test.hello",
            "test.world"
          )
        }

        subscription.cancel()
      }
    }

    "starting and stopping modules" should {
      "allow for all all modules to be stopped and started within the same process" in {
        10 times {
          val module = new DummyModule()
          Kamon.registerModule("dummy", module)
          Await.ready(Kamon.stopModules(), 5 seconds)
        }
      }
    }
  }

  override protected def beforeAll(): Unit = {
    Kamon.init()

    applyConfig(
      """
        |kamon.metric.tick-interval = 10 millis
        |test-metric-filter {
        |  includes = [ "test**" ]
        |}
        |
    """.stripMargin
    )
  }

  override protected def afterAll(): Unit = {
    reset()
    Kamon.stop()
  }

  class SeenMetricsReporter extends MetricReporter {
    @volatile private var count = 0
    @volatile private var seenMetrics = Seq.empty[String]

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      import snapshot._
      count += 1
      seenMetrics =
        counters.map(_.name) ++
        histograms.map(_.name) ++
        gauges.map(_.name) ++
        rangeSamplers.map(_.name) ++
        timers.map(_.name)
    }

    def metrics(): Seq[String] =
      seenMetrics

    def snapshotCount(): Int =
      count

    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
  }

  val dummyModuleCount = new AtomicLong(0L)

  class DummyModule extends Module {
    dummyModuleCount.incrementAndGet()

    override def reconfigure(newConfig: Config): Unit = {}
    override def stop(): Unit = dummyModuleCount.decrementAndGet()
  }
}
