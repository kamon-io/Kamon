package kamon.prometheus

import kamon.metric.{DynamicRange, HdrHistogram, MetricDistribution, MetricValue}
import kamon.metric.MeasurementUnit
import org.scalatest.{Matchers, WordSpec}
import kamon.metric.MeasurementUnit.{information, time, none}

class ScrapeDataBuilderSpec extends WordSpec with Matchers {

  "the scrape data builder formatter" should {
    "append units to the metric names when reporting values in the time dimension" in {
      val counterOne = MetricValue("counter-one", Map.empty, time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
          """
            |# TYPE counter_one_seconds_total counter
            |counter_one_seconds_total 10.0
            |# TYPE gauge_one_seconds gauge
            |gauge_one_seconds 20.0
          """.stripMargin.trim()
      }
    }

    "append units to the metric names when reporting values in the information dimension" in {
      val counterOne = MetricValue("counter-one", Map.empty, information.bytes, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, information.bytes, 20)

      builder()
      .appendCounters(Seq(counterOne))
      .appendGauges(Seq(gaugeOne))
      .build() should include {
        """
          |# TYPE counter_one_bytes_total counter
          |counter_one_bytes_total 10.0
          |# TYPE gauge_one_bytes gauge
          |gauge_one_bytes 20.0
        """.stripMargin.trim()
      }
    }

    "normalize tag names" in {
      val counterOne = MetricValue("counter-one", Map("tag.with.dots" -> "value"), time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder()
      .appendCounters(Seq(counterOne))
      .appendGauges(Seq(gaugeOne))
      .build() should include {
        """
          |# TYPE counter_one_seconds_total counter
          |counter_one_seconds_total{tag_with_dots="value"} 10.0
          |# TYPE gauge_one_seconds gauge
          |gauge_one_seconds 20.0
        """.stripMargin.trim()
      }
    }

    "append counters and group them together by metric name" in {
      val counterOne = MetricValue("counter-one", Map.empty, none, 10)
      val counterTwo = MetricValue("counter-two", Map.empty, none, 20)
      val counterOneWithTag = MetricValue("counter-one", Map("t" -> "v"), none, 30)

      builder().appendCounters(Seq(counterOne, counterTwo, counterOneWithTag)).build() should include {
        """
          |# TYPE counter_one_total counter
          |counter_one_total 10.0
          |counter_one_total{t="v"} 30.0
          |# TYPE counter_two_total counter
          |counter_two_total 20.0
        """.stripMargin.trim()
      }

    }

    "append gauges and group them together by metric name" in {
      val gaugeOne = MetricValue("gauge-one", Map.empty, none, 10)
      val gaugeTwo = MetricValue("gauge-two", Map.empty, none, 20)
      val gaugeTwoWithTags = MetricValue("gauge-two", Map("t" -> "v", "t2" -> "v2"), none, 30)

      builder().appendGauges(Seq(gaugeOne, gaugeTwo, gaugeTwoWithTags)).build() should include {
        """
          |# TYPE gauge_one gauge
          |gauge_one 10.0
          |# TYPE gauge_two gauge
          |gauge_two 20.0
          |gauge_two{t="v",t2="v2"} 30.0
        """.stripMargin.trim()
      }
    }

    "append histograms grouped together by metric name and with all their derived time series " in {
      val histogramOne = constantDistribution("histogram-one", Map.empty, none, 1, 10)
      val histogramTwo = constantDistribution("histogram-two", Map.empty, none, 1, 20)
      val histogramThree = constantDistribution("histogram-three", Map.empty, none, 5, 10)
      val histogramWithZero = constantDistribution("histogram-with-zero", Map.empty, none, 0, 10)

      builder(buckets = Seq(15D)).appendHistograms(Seq(histogramOne)).build() should include {
        """
          |# TYPE histogram_one histogram
          |histogram_one_bucket{le="15.0"} 10.0
          |histogram_one_bucket{le="+Inf"} 10.0
          |histogram_one_count 10.0
          |histogram_one_sum 55.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(5D, 10D, 15D, 20D)).appendHistograms(Seq(histogramTwo)).build() should include {
        """
          |# TYPE histogram_two histogram
          |histogram_two_bucket{le="5.0"} 5.0
          |histogram_two_bucket{le="10.0"} 10.0
          |histogram_two_bucket{le="15.0"} 15.0
          |histogram_two_bucket{le="20.0"} 20.0
          |histogram_two_bucket{le="+Inf"} 20.0
          |histogram_two_count 20.0
          |histogram_two_sum 210.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(3D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="3.0"} 0.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(3D, 50D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="3.0"} 0.0
          |histogram_three_bucket{le="50.0"} 6.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(3D, 50D, 60D, 70D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="3.0"} 0.0
          |histogram_three_bucket{le="50.0"} 6.0
          |histogram_three_bucket{le="60.0"} 6.0
          |histogram_three_bucket{le="70.0"} 6.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(7D)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="7.0"} 3.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(0.005D, 0.05D, 0.5D, 1D, 2D, 2.1D, 2.2D, 2.3D, 10D)).appendHistograms(Seq(histogramWithZero)).build() should include {
        """
          |# TYPE histogram_with_zero histogram
          |histogram_with_zero_bucket{le="0.005"} 1.0
          |histogram_with_zero_bucket{le="0.05"} 1.0
          |histogram_with_zero_bucket{le="0.5"} 1.0
          |histogram_with_zero_bucket{le="1.0"} 2.0
          |histogram_with_zero_bucket{le="2.0"} 3.0
          |histogram_with_zero_bucket{le="2.1"} 3.0
          |histogram_with_zero_bucket{le="2.2"} 3.0
          |histogram_with_zero_bucket{le="2.3"} 3.0
          |histogram_with_zero_bucket{le="10.0"} 11.0
          |histogram_with_zero_bucket{le="+Inf"} 11.0
          |histogram_with_zero_count 11.0
          |histogram_with_zero_sum 55.0
        """.stripMargin.trim()
      }
    }

    "include global custom tags from the Kamon.environment.tags" in {
      val counterOne = MetricValue("counter-one", Map("tag.with.dots" -> "value"), time.seconds, 10)
      val gaugeOne = MetricValue("gauge-one", Map.empty, time.seconds, 20)

      builder(environmentTags = Map("env_key" -> "env_value"))
      .appendCounters(Seq(counterOne))
      .appendGauges(Seq(gaugeOne))
      .build() should include {
        """
          |# TYPE counter_one_seconds_total counter
          |counter_one_seconds_total{tag_with_dots="value",env_key="env_value"} 10.0
          |# TYPE gauge_one_seconds gauge
          |gauge_one_seconds{env_key="env_value"} 20.0
        """.stripMargin.trim()
      }
    }

    "let environment tags have precedence over custom tags" in {
      val metric = MetricValue("some-metric", Map("custom.tag" -> "custom-value"), time.seconds, 10)

      builder(environmentTags = Map("custom.tag" -> "environment-value"))
        .appendCounters(Seq(metric))
        .appendGauges(Seq(metric))
        .build() should include {
        """
          |# TYPE some_metric_seconds_total counter
          |some_metric_seconds_total{custom_tag="environment-value"} 10.0
          |# TYPE some_metric_seconds gauge
          |some_metric_seconds{custom_tag="environment-value"} 10.0
        """.stripMargin.trim()
      }
    }
  }

  private def builder(buckets: Seq[java.lang.Double] = Seq(5D, 7D, 8D, 9D, 10D, 11D, 12D), environmentTags: Map[String, String] = Map.empty) = new ScrapeDataBuilder(
    PrometheusReporter.Configuration(false, "localhost", 1, buckets, buckets, buckets, false), environmentTags
  )

  private def constantDistribution(name: String, tags: Map[String, String], unit: MeasurementUnit, lower: Int, upper: Int): MetricDistribution = {
    val histogram = new HdrHistogram(name, tags, unit, DynamicRange.Default)
    for(value <- lower to upper) {
      histogram.record(value, 1)
    }

    histogram.snapshot(resetState = true)
  }
}
