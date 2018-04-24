package kamon.influxdb

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric._
import kamon.testkit.MetricInspection
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.scalatest._

import kamon.influxdb.InfluxDBCustomMatchers._

class InfluxDBReporterSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  "the InfluxDB reporter" should {
    "convert and post all metrics using the line protocol over HTTP" in {
      reporter.reconfigure(ConfigFactory.parseString(
        s"""
           |kamon.influxdb {
           |  hostname = ${influxDB.getHostName}
           |  port = ${influxDB.getPort}
           |
           |  additional-tags {
           |    service = no
           |    host = no
           |    instance = no
           |
           |    blacklisted-tags = [ "env", "context" ]
           |  }
           |}
      """.stripMargin
      ).withFallback(Kamon.config()))

      reporter.reportPeriodSnapshot(periodSnapshot)
      val reportedLines = influxDB.takeRequest(10, TimeUnit.SECONDS).getBody.readString(Charset.forName("UTF-8")).split("\n")

      val expectedLines = List(
        "custom.user.counter count=42i 1517000993",
        "jvm.heap-size value=150000000i 1517000993",
        "akka.actor.errors,path=as/user/actor count=10i 1517000993",
        "my.histogram,one=tag count=4i,sum=13i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993",
        "queue.monitor,one=tag count=4i,sum=13i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993"
      )

      reportedLines.sorted.zip(expectedLines.sorted) foreach {
        case (reported, expected) => reported should matchExpectedLineProtocolPoint(expected)
      }

    }
    "include the additional env tags if enabled" in {
      //enable env tags
      reporter.reconfigure(ConfigFactory.parseString(
        s"""
           |kamon.influxdb {
           |  hostname = ${influxDB.getHostName}
           |  port = ${influxDB.getPort}
           |  additional-tags {
           |    service = yes
           |    host = yes
           |    instance = yes
           |  }
           |}
      """.stripMargin
      ).withFallback(Kamon.config()))

      reporter.reportPeriodSnapshot(periodSnapshot)
      val reportedLines = influxDB.takeRequest(10, TimeUnit.SECONDS).getBody.readString(Charset.forName("UTF-8")).split("\n")

      val expectedLines = List(
        "custom.user.counter,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=42i 1517000993",
        "jvm.heap-size,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context value=150000000i 1517000993",
        "akka.actor.errors,path=as/user/actor,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=10i 1517000993",
        "my.histogram,one=tag,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=4i,sum=13i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993",
        "queue.monitor,one=tag,service=test-service,host=test.host,instance=test-instance,env=staging,context=test-context count=4i,sum=13i,min=1i,p50.0=2.0,p70.0=4.0,p90.0=6.0,p95.0=6.0,p99.0=6.0,p99.9=6.0,max=6i 1517000993"
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
  val periodSnapshot = new PeriodSnapshotBuilder()
    .from(from)
    .to(to)
    .counter("custom.user.counter", Map(), 42)
    .gauge("jvm.heap-size",         Map(), 150000000)
    .counter("akka.actor.errors",   Map("path" -> "as/user/actor"), 10)
    .histogram("my.histogram",      Map("one" -> "tag"), 1, 2, 4, 6)
    .rangeSampler("queue.monitor",  Map("one" -> "tag"), 1, 2, 4, 6)
    .build()


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
    reporter.start()
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

  class PeriodSnapshotBuilder extends MetricInspection {
    private var _from: Instant = Instant.ofEpochSecond(1)
    private var _to: Instant = Instant.ofEpochSecond(2)
    private var _counters: Seq[MetricValue] = Seq.empty
    private var _gauges: Seq[MetricValue] = Seq.empty
    private var _histograms: Seq[MetricDistribution] = Seq.empty
    private var _rangeSamplers: Seq[MetricDistribution] = Seq.empty

    def from(instant: Instant): PeriodSnapshotBuilder = {
      this._from = instant
      this
    }

    def to(instant: Instant): PeriodSnapshotBuilder = {
      this._to = instant
      this
    }

    def counter(name: String, tags: Map[String, String], value: Long): PeriodSnapshotBuilder = {
      _counters = _counters :+ MetricValue(name, tags, MeasurementUnit.none, value)
      this
    }

    def gauge(name: String, tags: Map[String, String], value: Long): PeriodSnapshotBuilder = {
      _gauges = _gauges :+ MetricValue(name, tags, MeasurementUnit.none, value)
      this
    }

    def histogram(name: String, tags: Map[String, String], values: Long*): PeriodSnapshotBuilder = {
      val temp = Kamon.histogram("temp")
      values.foreach(v => temp.record(v))
      _histograms = _histograms :+ MetricDistribution(name, tags, MeasurementUnit.time.nanoseconds, DynamicRange.Default, temp.distribution())
      this
    }

    def rangeSampler(name: String, tags: Map[String, String], values: Long*): PeriodSnapshotBuilder = {
      val temp = Kamon.histogram("temp")
      values.foreach(v => temp.record(v))
      _rangeSamplers = _rangeSamplers :+ MetricDistribution(name, tags, MeasurementUnit.time.nanoseconds, DynamicRange.Default, temp.distribution())
      this
    }


    def build(): PeriodSnapshot =
      PeriodSnapshot(_from, _to, MetricsSnapshot(_histograms, _rangeSamplers, _gauges, _counters))

  }
}
