package kamon

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.testkit.Reconfigure
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class ReporterRegistrySpec extends WordSpec with Matchers with Reconfigure with Eventually with BeforeAndAfterAll  {
  "The ReporterRegistry" when {
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

  abstract class DummyReporter extends MetricReporter {
    override def start(): Unit = {}
    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
  }

  class SeenMetricsReporter extends DummyReporter {
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
  }
}
