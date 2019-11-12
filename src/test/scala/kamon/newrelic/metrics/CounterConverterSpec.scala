package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.{Count, Metric => NewRelicMetric}
import org.scalatest.{Matchers, WordSpec}

class CounterConverterSpec extends WordSpec with Matchers {

  "the counter converter" should {
    "convert a counter with multiple instruments" in {
      val snapshot = TestMetricHelper.buildCounter
      val expectedAttrs = new Attributes()
        .put("description", snapshot.description)
        .put("dimensionName", "percentage")
        .put("magnitudeName", "percentage")
        .put("scaleFactor", 1.0)
        .put("foo", "bar")
        .put("sourceMetricType", "counter")

      val expected1: NewRelicMetric = new Count("flib", TestMetricHelper.value1, TestMetricHelper.start, TestMetricHelper.end, expectedAttrs);
      val expected2: NewRelicMetric = new Count("flib", TestMetricHelper.value2, TestMetricHelper.start, TestMetricHelper.end, expectedAttrs);

      val expectedResult: Seq[NewRelicMetric] = Seq(expected1, expected2)

      val result = CounterConverter.convert(TestMetricHelper.start, TestMetricHelper.end, snapshot)
      result shouldBe expectedResult
    }
  }

}
