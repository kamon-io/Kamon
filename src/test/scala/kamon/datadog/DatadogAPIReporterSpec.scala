package kamon.datadog

import java.time.Instant

import kamon.metric.{ MeasurementUnit, MetricValue, MetricsSnapshot, PeriodSnapshot }
import kamon.testkit.Reconfigure
import org.scalatest.{ Matchers, WordSpec }

class DatadogAPIReporterSpec extends WordSpec with Matchers with Reconfigure { reconfigure =>

  "the DatadogAPIReporter" should {

    "send counter metrics" in {
      val reporter = new DatadogAPIReporter()
      val now = Instant.ofEpochMilli(1523395554)
      val series = reporter.buildRequestBody(
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
      series shouldBe """{"series":[{"metric":"test.counter","interval":1,"points":[[1523394,0]],"type":"count","host":"test","tags":["service:kamon-application","env:staging","tag1:value1"]}]}"""

    }
  }

}
