/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.prometheus

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric._
import kamon.testkit.MetricInspection
import kamon.{Kamon, MetricReporter}
import org.scalatest.{Matchers, WordSpec}

class MetricOverrideReporterSpec extends WordSpec with Matchers with MetricInspection {

  "A MetricOverrideReporter" should {
    "apply metric overrides" in {
      report(histogram("default.metric-name", Map.empty)) { snapshot =>
        snapshot.histograms.head.name shouldEqual "new-metric-name"
      }

      report(rangeSampler("default.metric-name", Map.empty)) { snapshot =>
        snapshot.rangeSamplers.head.name shouldEqual "new-metric-name"
      }

      report(gauge("default.metric-name", Map.empty)) { snapshot =>
        snapshot.gauges.head.name shouldEqual "new-metric-name"
      }

      report(counter("default.metric-name", Map.empty)) { snapshot =>
        snapshot.counters.head.name shouldEqual "new-metric-name"
      }
    }

    "not modify metrics that do not appear in the override configuration" in {
      report(histogram("other-metric-name", Map.empty)) { snapshot =>
        snapshot.histograms.head.name shouldEqual "other-metric-name"
      }

      report(rangeSampler("other-metric-name", Map.empty)) { snapshot =>
        snapshot.rangeSamplers.head.name shouldEqual "other-metric-name"
      }

      report(gauge("other-metric-name", Map.empty)) { snapshot =>
        snapshot.gauges.head.name shouldEqual "other-metric-name"
      }

      report(counter("other-metric-name", Map.empty)) { snapshot =>
        snapshot.counters.head.name shouldEqual "other-metric-name"
      }
    }

    "apply metric tag deletes" in {
      report(histogram("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.tags should contain theSameElementsAs Map("keep-me" -> "bar")
      }

      report(rangeSampler("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.tags should contain theSameElementsAs Map("keep-me" -> "bar")
      }

      report(gauge("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.tags should contain theSameElementsAs Map("keep-me" -> "bar")
      }

      report(counter("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.tags should contain theSameElementsAs Map("keep-me" -> "bar")
      }
    }

    "not delete tags with the same name from other metrics" in {
      report(histogram("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.tags should contain theSameElementsAs Map("unwanted-tag" -> "foo", "keep-me" -> "bar")
      }

      report(rangeSampler("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.tags should contain theSameElementsAs Map("unwanted-tag" -> "foo", "keep-me" -> "bar")
      }

      report(gauge("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.tags should contain theSameElementsAs Map("unwanted-tag" -> "foo", "keep-me" -> "bar")
      }

      report(counter("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.tags should contain theSameElementsAs Map("unwanted-tag" -> "foo", "keep-me" -> "bar")
      }
    }

    "apply metric tag renames" in {
      report(histogram("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.tags should contain theSameElementsAs Map("new-name" -> "foo", "leave-me" -> "bar")
      }

      report(rangeSampler("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.tags should contain theSameElementsAs Map("new-name" -> "foo", "leave-me" -> "bar")
      }

      report(gauge("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.tags should contain theSameElementsAs Map("new-name" -> "foo", "leave-me" -> "bar")
      }

      report(counter("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.tags should contain theSameElementsAs Map("new-name" -> "foo", "leave-me" -> "bar")
      }
    }

    "not rename tags from other metrics" in {
      report(histogram("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.histograms.head.tags should contain theSameElementsAs Map("old-name" -> "foo")
      }

      report(rangeSampler("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.rangeSamplers.head.tags should contain theSameElementsAs Map("old-name" -> "foo")
      }

      report(gauge("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.gauges.head.tags should contain theSameElementsAs Map("old-name" -> "foo")
      }

      report(counter("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.counters.head.tags should contain theSameElementsAs Map("old-name" -> "foo")
      }
    }
  }

  val config = ConfigFactory.parseString(
    """
      |kamon.prometheus {
      |  metric-overrides {
      |    "default.metric-name" {
      |      name = new-metric-name
      |      delete-tags = []
      |      rename-tags {}
      |    }
      |    some-other-metric {
      |      delete-tags = [ unwanted-tag ]
      |      rename-tags {
      |         old-name = new-name
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

  val reporter = new DummyMetricReporter
  val wrapper = new MetricOverrideReporter(reporter, config)

  class DummyMetricReporter extends MetricReporter {
    override def start(): Unit = ()

    override def stop(): Unit = ()

    override def reconfigure(config: Config): Unit = ()

    var metrics: MetricsSnapshot = _

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      metrics = snapshot.metrics
    }
  }


  def report(periodSnapshot: PeriodSnapshot)(assertions: MetricsSnapshot => Unit): Unit = {
    wrapper.reportPeriodSnapshot(periodSnapshot)
    assertions(reporter.metrics)
  }

  val emptyDistribution = Kamon.histogram("test").distribution()
  val emptyPeriodSnapshot = PeriodSnapshot(Kamon.clock().instant(), Kamon.clock().instant(),
    MetricsSnapshot(Seq.empty, Seq.empty, Seq.empty, Seq.empty))

  def counter(metricName: String, tags: Map[String, String]): PeriodSnapshot = {
    emptyPeriodSnapshot.copy(metrics = emptyPeriodSnapshot.metrics
      .copy(counters = Seq(MetricValue(metricName, tags, MeasurementUnit.none, 1))))
  }

  def gauge(metricName: String, tags: Map[String, String]): PeriodSnapshot = {
    emptyPeriodSnapshot.copy(metrics = emptyPeriodSnapshot.metrics
      .copy(gauges = Seq(MetricValue(metricName, tags, MeasurementUnit.none, 1))))
  }

  def histogram(metricName: String, tags: Map[String, String]): PeriodSnapshot = {
    emptyPeriodSnapshot.copy(metrics = emptyPeriodSnapshot.metrics
      .copy(histograms = Seq(MetricDistribution(metricName, tags, MeasurementUnit.none, DynamicRange.Default, emptyDistribution))))
  }

  def rangeSampler(metricName: String, tags: Map[String, String]): PeriodSnapshot = {
    emptyPeriodSnapshot.copy(metrics = emptyPeriodSnapshot.metrics
      .copy(rangeSamplers = Seq(MetricDistribution(metricName, tags, MeasurementUnit.none, DynamicRange.Default, emptyDistribution))))
  }
}
