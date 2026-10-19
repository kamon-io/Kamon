package kamon.prometheus

import com.typesafe.config.ConfigFactory
import kamon.metric.MeasurementUnit.{information, none, time}
import kamon.metric.{MeasurementUnit, MetricSnapshot}
import kamon.prometheus.PrometheusSettings.{GaugeSettings, SummarySettings}
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder
import kamon.util.Filter.Glob
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ScrapeDataBuilderSpec extends AnyWordSpec with Matchers {

  "the scrape data builder formatter" should {
    "append units to the metric names when reporting values in the time dimension" in {
      val counterOne = MetricSnapshotBuilder.counter("counter-one", "", TagSet.Empty, time.seconds, 10)
      val gaugeOne = MetricSnapshotBuilder.gauge("gauge-one", "", TagSet.Empty, time.seconds, 20)

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
      val counterOne = MetricSnapshotBuilder.counter("counter-one", "", TagSet.Empty, information.bytes, 10)
      val gaugeOne = MetricSnapshotBuilder.gauge("gauge-one", "", TagSet.Empty, information.bytes, 20)

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
      val counterOne =
        MetricSnapshotBuilder.counter("app:counter-one", "", TagSet.of("tag.with.dots", "value"), time.seconds, 10)
      val gaugeOne =
        MetricSnapshotBuilder.gauge("gauge-one", "", TagSet.of("tag-with-dashes", "value"), time.seconds, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
        """
          |# TYPE app:counter_one_seconds_total counter
          |app:counter_one_seconds_total{tag_with_dots="value"} 10.0
          |# TYPE gauge_one_seconds gauge
          |gauge_one_seconds{tag_with_dashes="value"} 20.0
        """.stripMargin.trim()
      }
    }

    "escape backslash in tag values" in {
      val counter = MetricSnapshotBuilder.counter(
        "counter.with.backslash",
        "",
        TagSet.of("path", "c:\\users\\temp"),
        time.seconds,
        10
      )

      builder()
        .appendCounters(Seq(counter))
        .build() should include {

        """
          |# TYPE counter_with_backslash_seconds_total counter
          |counter_with_backslash_seconds_total{path="c:\\users\\temp"} 10.0
        """.stripMargin.trim()
      }
    }

    "not add extra '_total' postfix if metric name already ends with it when using units" in {
      val counterOne = MetricSnapshotBuilder.counter(
        "app:counter-one-seconds-total",
        "",
        TagSet.of("tag.with.dots", "value"),
        time.seconds,
        10
      )
      val gaugeOne =
        MetricSnapshotBuilder.gauge("gauge-one-seconds", "", TagSet.of("tag-with-dashes", "value"), time.seconds, 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
        """
          |# TYPE app:counter_one_seconds_total counter
          |app:counter_one_seconds_total{tag_with_dots="value"} 10.0
          |# TYPE gauge_one_seconds gauge
          |gauge_one_seconds{tag_with_dashes="value"} 20.0
        """.stripMargin.trim()
      }
    }
    "not add extra '_total' postfix if metric name already ends with it when not using units" in {
      val counterOne =
        MetricSnapshotBuilder.counter("app:counter-one-total", "", TagSet.of("tag.with.dots", "value"), 10)
      val gaugeOne = MetricSnapshotBuilder.gauge("gauge-one", "", TagSet.of("tag-with-dashes", "value"), 20)

      builder()
        .appendCounters(Seq(counterOne))
        .appendGauges(Seq(gaugeOne))
        .build() should include {
        """
          |# TYPE app:counter_one_total counter
          |app:counter_one_total{tag_with_dots="value"} 10.0
          |# TYPE gauge_one gauge
          |gauge_one{tag_with_dashes="value"} 20.0
        """.stripMargin.trim()
      }
    }

//    "append counters and group them together by metric name" in {
//      val counterOne = MetricValue("counter-one", Map.empty, none, 10)
//      val counterTwo = MetricValue("counter-two", Map.empty, none, 20)
//      val counterOneWithTag = MetricValue("counter-one", Map("t" -> "v"), none, 30)
//
//      builder().appendCounters(Seq(counterOne, counterTwo, counterOneWithTag)).build() should include {
//        """
//          |# TYPE counter_one_total counter
//          |counter_one_total 10.0
//          |counter_one_total{t="v"} 30.0
//          |# TYPE counter_two_total counter
//          |counter_two_total 20.0
//        """.stripMargin.trim()
//      }
//
//    }

//    "append gauges and group them together by metric name" in {
//      val gaugeOne = MetricValue("gauge-one", Map.empty, none, 10)
//      val gaugeTwo = MetricValue("gauge-two", Map.empty, none, 20)
//      val gaugeTwoWithTags = MetricValue("gauge-two", Map("t" -> "v", "t2" -> "v2"), none, 30)
//
//      builder().appendGauges(Seq(gaugeOne, gaugeTwo, gaugeTwoWithTags)).build() should include {
//        """
//          |# TYPE gauge_one gauge
//          |gauge_one 10.0
//          |# TYPE gauge_two gauge
//          |gauge_two 20.0
//          |gauge_two{t="v",t2="v2"} 30.0
//        """.stripMargin.trim()
//      }
//    }

    "read custom bucket configurations" in {
      val config = PrometheusSettings.readSettings(ConfigFactory.parseString(
        """
          |kamon.prometheus.buckets.custom {
          |  singleword = [1, 2, 3]
          |  "with.several.dots" = [3, 2, 1]
          |}
        """.stripMargin
      ).withFallback(ConfigFactory.defaultReference()).getConfig("kamon.prometheus"))

      config.customBuckets should contain allOf (
        ("singleword" -> Seq(1d, 2d, 3d)),
        ("with.several.dots" -> Seq(3d, 2d, 1d))
      )

    }

    "override histogram buckets with custom configuration" in {
      val customBucketsHistogram = constantDistribution("histogram.custom-buckets", none, 1, 10)

      builder(customBuckets = Map("histogram.custom-buckets" -> Seq(1d, 2d, 4d))).appendHistograms(Seq(
        customBucketsHistogram
      )).build() should include {
        """
          |# TYPE histogram_custom_buckets histogram
          |histogram_custom_buckets_bucket{le="1.0"} 1.0
          |histogram_custom_buckets_bucket{le="2.0"} 2.0
          |histogram_custom_buckets_bucket{le="4.0"} 4.0
          |histogram_custom_buckets_bucket{le="+Inf"} 10.0
          |histogram_custom_buckets_count 10.0
          |histogram_custom_buckets_sum 55.0
        """.stripMargin.trim()
      }
    }

    "append histograms grouped together by metric name and with all their derived time series " in {
      val histogramOne = constantDistribution("histogram-one", none, 1, 10)
      val histogramTwo = constantDistribution("histogram-two", none, 1, 20)
      val histogramThree = constantDistribution("histogram-three", none, 5, 10)
      val histogramWithZero = constantDistribution("histogram-with-zero", none, 0, 10)

      builder(buckets = Seq(15d)).appendHistograms(Seq(histogramOne)).build() should include {
        """
          |# TYPE histogram_one histogram
          |histogram_one_bucket{le="15.0"} 10.0
          |histogram_one_bucket{le="+Inf"} 10.0
          |histogram_one_count 10.0
          |histogram_one_sum 55.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(5d, 10d, 15d, 20d)).appendHistograms(Seq(histogramTwo)).build() should include {
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

      builder(buckets = Seq(3d)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="3.0"} 0.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(3d, 50d)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="3.0"} 0.0
          |histogram_three_bucket{le="50.0"} 6.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(3d, 50d, 60d, 70d)).appendHistograms(Seq(histogramThree)).build() should include {
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

      builder(buckets = Seq(7d)).appendHistograms(Seq(histogramThree)).build() should include {
        """
          |# TYPE histogram_three histogram
          |histogram_three_bucket{le="7.0"} 3.0
          |histogram_three_bucket{le="+Inf"} 6.0
          |histogram_three_count 6.0
          |histogram_three_sum 45.0
        """.stripMargin.trim()
      }

      builder(buckets = Seq(0.005d, 0.05d, 0.5d, 1d, 2d, 2.1d, 2.2d, 2.3d, 10d)).appendHistograms(
        Seq(histogramWithZero)
      ).build() should include {
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
    "append summaries per metric" in {
      val histogramWithHundredEntries = constantDistribution("distribution-100", none, 1, 100)
      val result = builder(withSummary = Seq("**")).appendHistograms(Seq(histogramWithHundredEntries)).build()
      result should include {
        """|# TYPE distribution_100 summary
          |distribution_100{quantile="0.5"} 50.0
          |distribution_100{quantile="0.75"} 75.0
          |distribution_100{quantile="0.95"} 95.0
          |distribution_100{quantile="0.99"} 99.0
          |distribution_100_count 100.0
          |distribution_100_sum 5050.0
          |""".stripMargin
      }
      result should not include {
        """|# TYPE distribution_100 histogram
          |distribution_100_bucket{le="5.0"} 5.0
          |distribution_100_bucket{le="7.0"} 7.0
          |distribution_100_bucket{le="8.0"} 8.0
          |distribution_100_bucket{le="9.0"} 9.0
          |distribution_100_bucket{le="10.0"} 10.0
          |distribution_100_bucket{le="11.0"} 11.0
          |distribution_100_bucket{le="12.0"} 12.0
          |distribution_100_bucket{le="+Inf"} 100.0
          |""".stripMargin
      }
    }

    "append summary only for matching metrics" in {
      val histogramWithHundredEntries = constantDistribution("firstMetric", none, 1, 100)
      val histogramWithHundredEntriesTwo = constantDistribution("secondMetric", none, 1, 100)
      val result = builder(withSummary = Seq("first*"))
        .appendHistograms(Seq(histogramWithHundredEntries, histogramWithHundredEntriesTwo))
        .build()
      result should include {
        """|# TYPE firstMetric summary
           |firstMetric{quantile="0.5"} 50.0
           |firstMetric{quantile="0.75"} 75.0
           |firstMetric{quantile="0.95"} 95.0
           |firstMetric{quantile="0.99"} 99.0
           |firstMetric_count 100.0
           |firstMetric_sum 5050.0
           |""".stripMargin
      }
      result should not include {
        """|# TYPE secondMetric summary
           |firstMetric{quantile="0.5"} 50.0
           |firstMetric{quantile="0.75"} 75.0
           |firstMetric{quantile="0.95"} 95.0
           |firstMetric{quantile="0.99"} 99.0
           |firstMetric_count 100.0
           |firstMetric_sum 5050.0
           |""".stripMargin
      }
      result should include {
        """|# TYPE secondMetric histogram
           |secondMetric_bucket{le="5.0"} 5.0
           |secondMetric_bucket{le="7.0"} 7.0
           |secondMetric_bucket{le="8.0"} 8.0
           |secondMetric_bucket{le="9.0"} 9.0
           |secondMetric_bucket{le="10.0"} 10.0
           |secondMetric_bucket{le="11.0"} 11.0
           |secondMetric_bucket{le="12.0"} 12.0
           |secondMetric_bucket{le="+Inf"} 100.0
           |""".stripMargin
      }

    }

    "append gauges only for matching metrics" in {
      val histogramWithHundredEntries = constantDistribution("firstMetric", none, 1, 100)
      val histogramWithHundredEntriesTwo = constantDistribution("secondMetric", none, 1, 100)
      val result = builder(withGauges = Seq("first*"))
        .appendDistributionMetricsAsGauges(Seq(histogramWithHundredEntries, histogramWithHundredEntriesTwo))
        .build()

      result should include {
        """|# TYPE firstMetric_min gauge
           |firstMetric_min 1.0
           |""".stripMargin
      }
      result should include {
        """|# TYPE firstMetric_max gauge
           |firstMetric_max 100.0
           |""".stripMargin
      }
      result should include {
        """|# TYPE firstMetric_avg gauge
           |firstMetric_avg 50.0
           |""".stripMargin
      }

      result should not include {
        """|# TYPE secondMetric_min gauge
           |""".stripMargin
      }
      result should not include {
        """|# TYPE secondMetric_max gauge
           |""".stripMargin
      }
      result should not include {
        """|# TYPE secondMetric_avg gauge
           |""".stripMargin
      }
    }

    "include global custom tags from the Kamon.environment.tags" in {
      val counterOne =
        MetricSnapshotBuilder.counter("counter-one", "", TagSet.of("tag.with.dots", "value"), time.seconds, 10)
      val gaugeOne = MetricSnapshotBuilder.gauge("gauge-one", "", TagSet.Empty, time.seconds, 20)

      builder(environmentTags = TagSet.of("env_key", "env_value"))
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
      val counter =
        MetricSnapshotBuilder.counter("some-metric", "", TagSet.of("custom.tag", "custom-value"), time.seconds, 10)
      val gauge =
        MetricSnapshotBuilder.gauge("some-metric", "", TagSet.of("custom.tag", "custom-value"), time.seconds, 10d)

      builder(environmentTags = TagSet.of("custom.tag", "environment-value"))
        .appendCounters(Seq(counter))
        .appendGauges(Seq(gauge))
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

  private def builder(
    buckets: Seq[java.lang.Double] = Seq(5d, 7d, 8d, 9d, 10d, 11d, 12d),
    customBuckets: Map[String, Seq[java.lang.Double]] = Map("histogram.custom-buckets" -> Seq(1d, 3d)),
    environmentTags: TagSet = TagSet.Empty,
    withSummary: Seq[String] = Seq.empty,
    withGauges: Seq[String] = Seq.empty
  ) = {
    new ScrapeDataBuilder(
      PrometheusSettings.Generic(
        buckets,
        buckets,
        buckets,
        buckets,
        customBuckets,
        false,
        SummarySettings(Seq(0.5, 0.75, 0.95, 0.99), withSummary.map(Glob)),
        GaugeSettings(withGauges.map(Glob))
      ),
      environmentTags
    )
  }

  private def constantDistribution(
    name: String,
    unit: MeasurementUnit,
    lower: Long,
    upper: Long
  ): MetricSnapshot.Distributions =
    MetricSnapshotBuilder.histogram(name, "", TagSet.Empty)((lower to upper): _*)
}
