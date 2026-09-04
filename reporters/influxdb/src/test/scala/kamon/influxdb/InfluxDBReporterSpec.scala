package kamon.influxdb

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.influxdb.InfluxDBCustomMatchers._
import kamon.metric._
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

class InfluxDBReporterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  "the InfluxDB reporter" should {
    "convert and post all metrics using the line protocol over HTTP" in {
      reporter.reconfigure(ConfigFactory.parseString(
        s"""
           |kamon.influxdb {
           |  hostname = ${influxDB.getHostName}
           |  port = ${influxDB.getPort}
           |
           |  environment-tags {
           |    include-service = no
           |    include-host = no
           |    include-instance = no
           |
           |    exclude = [ "env", "context" ]
           |  }
           |}
      """.stripMargin
      ).withFallback(Kamon.config()))

      reporter.reportPeriodSnapshot(periodSnapshot)
      val reportedLines =
        influxDB.takeRequest(10, TimeUnit.SECONDS).getBody.readString(Charset.forName("UTF-8")).split("\n")

      val expectedLines = List(
        "custom.user.counter count=42i 1517000993",
        "jvm.heap-size value=1.5E8 1517000993",
        "akka.actor.errors,path=as/user/actor count=10i 1517000993",
        "my.histogram,one=tag count=4i,sum=13i,mean=3i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993",
        "queue.monitor,one=tag count=4i,sum=13i,mean=3i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993"
      )

      reportedLines.sorted.zip(expectedLines.sorted) foreach {
        case (reported, expected) => reported should matchExpectedLineProtocolPoint(expected)
      }

    }
    "include the additional env tags if enabled" in {
      // enable env tags
      reporter.reconfigure(ConfigFactory.parseString(
        s"""
           |kamon.influxdb {
           |  hostname = ${influxDB.getHostName}
           |  port = ${influxDB.getPort}
           |  environment-tags {
           |    include-service = yes
           |    include-host = yes
           |    include-instance = yes
           |  }
           |}
      """.stripMargin
      ).withFallback(Kamon.config()))

      reporter.reportPeriodSnapshot(periodSnapshot)
      val reportedLines =
        influxDB.takeRequest(10, TimeUnit.SECONDS).getBody.readString(Charset.forName("UTF-8")).split("\n")

      val expectedLines = List(
        "custom.user.counter,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=42i 1517000993",
        "jvm.heap-size,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context value=1.5E8 1517000993",
        "akka.actor.errors,path=as/user/actor,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=10i 1517000993",
        "my.histogram,one=tag,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=4i,sum=13i,mean=3i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993",
        "queue.monitor,one=tag,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=4i,sum=13i,mean=3i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993"
      )

      reportedLines.sorted.zip(expectedLines.sorted) foreach {
        case (reported, expected) => reported should matchExpectedLineProtocolPoint(expected)
      }
    }
  }

  val influxDB = new MockWebServer()
  val reporter = new InfluxDBReporter()

  val from = Instant.ofEpochSecond(1517000974)
  val to = Instant.ofEpochSecond(1517000993)

  MetricSnapshotBuilder.counter("custom.user.counter", TagSet.Empty, 42)
  val periodSnapshot = PeriodSnapshot.apply(
    from,
    to,
    counters = MetricSnapshotBuilder.counter("custom.user.counter", TagSet.Empty, 42) ::
      MetricSnapshotBuilder.counter("akka.actor.errors", TagSet.of("path", "as/user/actor"), 10) ::
      Nil,
    gauges = MetricSnapshotBuilder.gauge("jvm.heap-size", TagSet.Empty, 150000000) :: Nil,
    histograms = MetricSnapshotBuilder.histogram("my.histogram", TagSet.of("one", "tag"))(1, 2, 4, 6) :: Nil,
    timers = Nil,
    rangeSamplers = MetricSnapshotBuilder.histogram("queue.monitor", TagSet.of("one", "tag"))(1, 2, 4, 6) :: Nil
  )

  override protected def beforeAll(): Unit = {
    influxDB.enqueue(new MockResponse().setResponseCode(204))
    influxDB.enqueue(new MockResponse().setResponseCode(204))
    influxDB.start()
    Kamon.reconfigure(ConfigFactory.parseString(
      s"""
         |kamon.environment {
         |    host = "test.host"
         |    service = "test-service"
         |    instance = "test-instance"
         |    tags = {
         |      env = "staging"
         |      context = "test-context"
         |    }
         |}
       """.stripMargin
    ).withFallback(Kamon.config()))
    reporter.reconfigure(ConfigFactory.parseString(
      s"""
        |kamon.influxdb {
        |  hostname = ${influxDB.getHostName}
        |  port = ${influxDB.getPort}
        |}
      """.stripMargin
    ).withFallback(Kamon.config()))
  }

  override protected def afterAll(): Unit = {
    influxDB.shutdown()
  }
}
