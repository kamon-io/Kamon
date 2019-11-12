package kamon.newrelic.metrics

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.metrics.Gauge
import org.scalatest.{Matchers, WordSpec}

class GaugeConverterSpec extends WordSpec with Matchers {

  "gauge converter" should {
    "convert a gauge" in {
      val timestamp: Long = System.currentTimeMillis()
      val kamonGauge = TestMetricHelper.buildGauge
      val attributes = new Attributes()
        .put("description", "another one")
        .put("magnitudeName", "finch")
        .put("dimensionName", "information")
        .put("scaleFactor", 11.0)
        .put("foo", "bar")
        .put("sourceMetricType", "gauge")
      val expectedGauge = new Gauge("shirley", 15.6d, timestamp, attributes)
      val result = GaugeConverter.convert(timestamp, kamonGauge)
      result shouldBe Seq(expectedGauge)
    }
  }

}
