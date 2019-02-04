package kamon.datadog

import java.time.Instant

import kamon.Kamon
import kamon.metric.{ MeasurementUnit, MetricValue, MetricsSnapshot, PeriodSnapshot }
import kamon.testkit.Reconfigure
import okhttp3.mockwebserver.MockResponse
import org.scalatest.Matchers
import play.api.libs.json.Json

class DatadogAPIReporterSpec extends AbstractHttpReporter with Matchers with Reconfigure {

  "the DatadogAPIReporter" should {
    val reporter = new DatadogAPIReporter()
    val now = Instant.ofEpochMilli(1523395554)

    reporter.start()

    "sends counter metrics" in {
      val baseUrl = mockResponse("/test", new MockResponse().setStatus("HTTP/1.1 200 OK"))
      applyConfig("kamon.datadog.http.api-url = \"" + baseUrl + "\"")
      applyConfig("kamon.datadog.http.api-key = \"dummy\"")

      reporter.reconfigure(Kamon.config())

      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
          now.minusMillis(1000), now, MetricsSnapshot.apply(
            Nil,
            Nil,
            Nil,
            Seq(
              MetricValue.apply("test.counter", Map("tag1" -> "value1"), MeasurementUnit.none, 0)
            )

          )
        )
      )
      val request = server.takeRequest()
      request.getRequestUrl.toString shouldEqual baseUrl + "?api_key=dummy"
      Json.parse(request.getBody().readUtf8()) shouldEqual Json.parse("""{"series":[{"metric":"test.counter","interval":1,"points":[[1523394,0]],"type":"count","host":"test","tags":["service:kamon-application","env:staging","tag1:value1"]}]}""")

    }

    reporter.stop()

  }

}
