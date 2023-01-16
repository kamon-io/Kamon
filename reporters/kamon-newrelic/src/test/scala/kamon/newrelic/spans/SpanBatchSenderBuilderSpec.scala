/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import com.typesafe.config.ConfigValueFactory
import kamon.Kamon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import scala.collection.JavaConverters._

class SpanBatchSenderBuilderSpec extends AnyWordSpec with Matchers {

  "the span batch sender builder" should {
    def createSenderConfiguration(configMap: Map[String, AnyRef]) = {
      val nrConfig = ConfigValueFactory.fromMap((Map("enable-audit-logging" -> false) ++ configMap).asJava)
      val config = Kamon.config().withValue("kamon.newrelic", nrConfig)
      new SimpleSpanBatchSenderBuilder().buildConfig(config)
    }

    "use insights insert key" in {
      val result = createSenderConfiguration(Map("nr-insights-insert-key" -> "insights"))
      assert("insights" == result.getApiKey)
      assert(!result.useLicenseKey)
    }
    "use license key" in {
      val result = createSenderConfiguration(Map("license-key" -> "license"))
      assert("license" == result.getApiKey)
      assert(result.useLicenseKey)
    }
    "default the uri if not configured" in {
      val result = createSenderConfiguration(Map("nr-insights-insert-key" -> "none"))
      assert(new URL("https://trace-api.newrelic.com/trace/v1") == result.getEndpointUrl)
    }
    "be able to override the ingest uri" in {
      val result = createSenderConfiguration(Map(
        "nr-insights-insert-key" -> "none",
        "span-ingest-uri" -> "https://example.com/foo",
      ))
      assert(new URL("https://example.com/foo") == result.getEndpointUrl)
    }
  }
}
