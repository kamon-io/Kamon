package kamon.datadog

import kamon.Kamon
import kamon.metric.Distribution.Percentile
import kamon.metric._
import kamon.module.ModuleFactory
import kamon.tag.TagSet
import kamon.testkit.Reconfigure
import okhttp3.mockwebserver.MockResponse
import okio.{InflaterSource, Okio}
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.time.Instant
import java.util.zip.Inflater
import scala.concurrent.ExecutionContext

class DatadogAPIReporterSpec extends AbstractHttpReporter with Matchers with Reconfigure {

  "the DatadogAPIReporter" should {

    val reporter =
      new DatadogAPIReporterFactory().create(ModuleFactory.Settings(Kamon.config(), ExecutionContext.global))
    val now = Instant.ofEpochMilli(1523395554)
    val examplePeriod = PeriodSnapshot.apply(
      now.minusMillis(1000),
      now,
      MetricSnapshot.ofValues[Long](
        "test.counter",
        "test",
        Metric.Settings.ForValueInstrument(MeasurementUnit.none, java.time.Duration.ZERO),
        Instrument.Snapshot.apply(TagSet.of("tag1", "value1"), 0L) :: Nil
      ) :: Nil,
      Nil,
      Nil,
      Nil,
      Nil
    )

    "handle retries on retriable HTTP status codes" in {
      val baseUrl = mockResponse("/test", new MockResponse().setResponseCode(429))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = false")
      applyConfig("kamon.datadog.api.init-retry-delay = 100 milliseconds")
      applyConfig("kamon.datadog.api.retries = 0")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriod)

      server.getRequestCount shouldEqual 1
      server.takeRequest()

      applyConfig("kamon.datadog.api.retries = 3")
      reporter.reconfigure(Kamon.config())

      mockResponse(new MockResponse().setResponseCode(429))
      mockResponse(new MockResponse().setResponseCode(503))
      mockResponse(new MockResponse().setResponseCode(504))
      reporter.reportPeriodSnapshot(examplePeriod)
      Thread.sleep(1000)
      server.takeRequest()
      server.takeRequest()
      server.takeRequest()
      server.getRequestCount shouldEqual 4
    }

    val examplePeriodWithDistributions: PeriodSnapshot = {
      val distributionExample = new Distribution {
        override def dynamicRange: DynamicRange = ???
        override def min: Long = 0
        override def max: Long = 10
        override def sum: Long = 100
        override def count: Long = 5
        override def percentile(rank: Double): Distribution.Percentile = new Percentile {
          override def rank: Double = 0
          override def value: Long = 0
          override def countAtRank: Long = 0
        }
        override def percentiles: Seq[Distribution.Percentile] = ???
        override def percentilesIterator: Iterator[Distribution.Percentile] = ???
        override def buckets: Seq[Distribution.Bucket] = ???
        override def bucketsIterator: Iterator[Distribution.Bucket] = ???
      }
      PeriodSnapshot.apply(
        now.minusMillis(1000),
        now,
        Nil,
        Nil,
        Nil,
        MetricSnapshot.ofDistributions(
          "test.timer",
          "test",
          Metric.Settings.ForDistributionInstrument(
            MeasurementUnit.none,
            java.time.Duration.ZERO,
            DynamicRange.Default
          ),
          Instrument.Snapshot.apply(TagSet.Empty, distributionExample) :: Nil
        ) :: Nil,
        Nil
      )
    }

    "sends metrics - compressed" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = true")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriod)

      val request = server.takeRequest()

      val decompressedBody =
        Okio.buffer(new InflaterSource(request.getBody.buffer(), new Inflater())).readByteString().utf8()

      Json.parse(decompressedBody) shouldEqual Json
        .parse(
          """{"series":[{"metric":"test.counter","interval":1,"points":[[1523394,0]],"type":"count","host":"test","tags":["env:staging","service:kamon-application","tag1:value1"]}]}"""
        )
    }

    "sends counter metrics" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = false")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriod)
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      request.getMethod shouldEqual "POST"
      Json.parse(request.getBody.readUtf8()) shouldEqual Json
        .parse(
          """{"series":[{"metric":"test.counter","interval":1,"points":[[1523394,0]],"type":"count","host":"test","tags":["env:staging","service:kamon-application","tag1:value1"]}]}"""
        )

    }

    "send timer metrics with the p95 percentile by default" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = false")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriodWithDistributions)
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      request.getMethod shouldEqual "POST"
      Json.parse(request.getBody.readUtf8()) shouldEqual Json
        .parse(
          """{"series":[
            |{"metric":"test.timer.avg","interval":1,"points":[[1523394,20]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.count","interval":1,"points":[[1523394,5]],"type":"count","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.median","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.95percentile","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.max","interval":1,"points":[[1523394,10]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.min","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]}]}""".stripMargin
        )
    }

    "send timer metrics allowing configuration of percentiles to submit" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = false")
      applyConfig("kamon.datadog.percentiles = [95.0, 99, 94.5]")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriodWithDistributions)
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      request.getMethod shouldEqual "POST"
      Json.parse(request.getBody.readUtf8()) shouldEqual Json
        .parse(
          """{"series":[
            |{"metric":"test.timer.avg","interval":1,"points":[[1523394,20]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.count","interval":1,"points":[[1523394,5]],"type":"count","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.median","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.95percentile","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.99percentile","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.94.5percentile","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.max","interval":1,"points":[[1523394,10]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.min","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]}]}""".stripMargin
        )
    }

    "send timer metrics without percentiles" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")
      applyConfig("kamon.datadog.api.compression = false")
      applyConfig("kamon.datadog.percentiles = []")
      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(examplePeriodWithDistributions)
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      request.getMethod shouldEqual "POST"
      Json.parse(request.getBody.readUtf8()) shouldEqual Json
        .parse(
          """{"series":[
            |{"metric":"test.timer.avg","interval":1,"points":[[1523394,20]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.count","interval":1,"points":[[1523394,5]],"type":"count","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.median","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.max","interval":1,"points":[[1523394,10]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]},
            |{"metric":"test.timer.min","interval":1,"points":[[1523394,0]],"type":"gauge","host":"test","tags":["env:staging","service:kamon-application"]}]}""".stripMargin
        )
    }

    reporter.stop()

  }

}
