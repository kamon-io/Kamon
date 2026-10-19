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
import kamon.module.MetricReporter
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetricOverrideReporterSpec extends AnyWordSpec with Matchers with MetricInspection.Syntax
    with InstrumentInspection.Syntax with KamonTestSnapshotSupport {

  "A MetricOverrideReporter" should {
    "apply metric overrides" in {
      report(histogram("default.metric-name")) { snapshot =>
        snapshot.histograms.head.name shouldEqual "new-metric-name"
      }

      report(rangeSampler("default.metric-name")) { snapshot =>
        snapshot.rangeSamplers.head.name shouldEqual "new-metric-name"
      }

      report(gauge("default.metric-name")) { snapshot =>
        snapshot.gauges.head.name shouldEqual "new-metric-name"
      }

      report(counter("default.metric-name")) { snapshot =>
        snapshot.counters.head.name shouldEqual "new-metric-name"
      }
    }

    "not modify metrics that do not appear in the override configuration" in {
      report(histogram("other-metric-name")) { snapshot =>
        snapshot.histograms.head.name shouldEqual "other-metric-name"
      }

      report(rangeSampler("other-metric-name")) { snapshot =>
        snapshot.rangeSamplers.head.name shouldEqual "other-metric-name"
      }

      report(gauge("other-metric-name")) { snapshot =>
        snapshot.gauges.head.name shouldEqual "other-metric-name"
      }

      report(counter("other-metric-name")) { snapshot =>
        snapshot.counters.head.name shouldEqual "other-metric-name"
      }
    }

    "apply metric tag deletes" in {
      report(histogram("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.instruments.head.tags shouldBe TagSet.of("keep-me", "bar")
      }

      report(rangeSampler("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.instruments.head.tags shouldBe TagSet.of("keep-me", "bar")
      }

      report(gauge("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.instruments.head.tags shouldBe TagSet.of("keep-me", "bar")
      }

      report(counter("some-other-metric", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.instruments.head.tags shouldBe TagSet.of("keep-me", "bar")
      }
    }

    "not delete tags with the same name from other metrics" in {
      report(histogram("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.instruments.head.tags shouldBe TagSet.of("unwanted-tag", "foo").withTag(
          "keep-me",
          "bar"
        )
      }

      report(rangeSampler("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.instruments.head.tags shouldBe TagSet.of("unwanted-tag", "foo").withTag(
          "keep-me",
          "bar"
        )
      }

      report(gauge("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.instruments.head.tags shouldBe TagSet.of("unwanted-tag", "foo").withTag("keep-me", "bar")
      }

      report(counter("other-metric-name", Map("unwanted-tag" -> "foo", "keep-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.instruments.head.tags shouldBe TagSet.of("unwanted-tag", "foo").withTag("keep-me", "bar")
      }
    }

    "apply metric tag renames" in {
      report(histogram("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.histograms.head.instruments.head.tags shouldBe TagSet.of("new-name", "foo").withTag("leave-me", "bar")
      }

      report(rangeSampler("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.rangeSamplers.head.instruments.head.tags shouldBe TagSet.of("new-name", "foo").withTag(
          "leave-me",
          "bar"
        )
      }

      report(gauge("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.gauges.head.instruments.head.tags shouldBe TagSet.of("new-name", "foo").withTag("leave-me", "bar")
      }

      report(counter("some-other-metric", Map("old-name" -> "foo", "leave-me" -> "bar"))) { snapshot =>
        snapshot.counters.head.instruments.head.tags shouldBe TagSet.of("new-name", "foo").withTag("leave-me", "bar")
      }
    }

    "not rename tags from other metrics" in {
      report(histogram("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.histograms.head.instruments.head.tags shouldBe TagSet.of("old-name", "foo")
      }

      report(rangeSampler("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.rangeSamplers.head.instruments.head.tags shouldBe TagSet.of("old-name", "foo")
      }

      report(gauge("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.gauges.head.instruments.head.tags shouldBe TagSet.of("old-name", "foo")
      }

      report(counter("other-metric-name", Map("old-name" -> "foo"))) { snapshot =>
        snapshot.counters.head.instruments.head.tags shouldBe TagSet.of("old-name", "foo")
      }
    }
  }

  val config: Config = ConfigFactory.parseString(
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
    """.stripMargin
  )

  val reporter = new DummyMetricReporter
  val wrapper = new MetricOverrideReporter(reporter, config)

  class DummyMetricReporter extends MetricReporter {
    override def stop(): Unit = ()
    override def reconfigure(config: Config): Unit = ()

    var latestSnapshot: PeriodSnapshot = _

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      latestSnapshot = snapshot
    }
  }

  def report(periodSnapshot: PeriodSnapshot)(assertions: PeriodSnapshot => Unit): Unit = {
    wrapper.reportPeriodSnapshot(periodSnapshot)
    assertions(reporter.latestSnapshot)
  }

}
