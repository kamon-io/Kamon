package kamon.datadog

import java.time.Instant

import kamon.Kamon
import kamon.metric.{ Instrument, MeasurementUnit, Metric, MetricSnapshot, PeriodSnapshot }
import kamon.module.ModuleFactory
import kamon.tag.TagSet
import kamon.testkit.Reconfigure
import okhttp3.mockwebserver.MockResponse
import org.scalatest.Matchers
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class DatadogAPIReporterSpec extends AbstractHttpReporter with Matchers with Reconfigure {

  "the DatadogAPIReporter" should {
    val reporter = new DatadogAPIReporterFactory().create(ModuleFactory.Settings(Kamon.config(), ExecutionContext.global))
    val now = Instant.ofEpochMilli(1523395554)

    "sends counter metrics" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.api.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.api.api-key = \"dummy\"")

      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
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
      )
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      request.getMethod shouldEqual "POST"
      Json.parse(request.getBody().readUtf8()) shouldEqual Json
        .parse(
          """{"series":[{"metric":"test.counter","interval":1,"points":[[1523394,0]],"type":"count","host":"test","tags":["env:staging","service:kamon-application","tag1:value1"]}]}"""
        )

    }

    reporter.stop()

  }

}
