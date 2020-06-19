/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import java.net.URL

import com.typesafe.config.ConfigValueFactory
import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters._

class SpanBatchSenderBuilderSpec extends WordSpec with Matchers {

  "the span batch sender builder" should {
    "default the uri if not configured" in {
      val nrConfig = ConfigValueFactory.fromMap(Map(
        "enable-audit-logging" -> false,
        "nr-insights-insert-key" -> "secret"
      ).asJava)

      val config = Kamon.config().withValue("kamon.newrelic", nrConfig)
      val result = new SimpleSpanBatchSenderBuilder().buildConfig(config)
      assert(new URL("https://trace-api.newrelic.com/trace/v1") == result.getEndpointUrl)
    }
    "be able to override the ingest uri" in {
      val nrConfig = ConfigValueFactory.fromMap(Map(
        "enable-audit-logging" -> false,
        "span-ingest-uri" -> "https://example.com/foo",
        "nr-insights-insert-key" -> "secret"
      ).asJava)

      val config = Kamon.config().withValue("kamon.newrelic", nrConfig)
      val result = new SimpleSpanBatchSenderBuilder().buildConfig(config)
      assert(new URL("https://example.com/foo") == result.getEndpointUrl)
    }
  }
}
