/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kamon.otel

import com.typesafe.config.ConfigFactory
import io.opentelemetry.sdk.resources.Resource
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

/**
  * Tests for the [[TraceService]]
  */
class TraceServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with Utils {
  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(15, Millis))

  private val resource = Resource.builder()
    .put("service.name", "TestService")
    .put("telemetry.sdk.name", "kamon")
    .put("telemetry.sdk.language", "scala")
    .put("telemetry.sdk.version", "0.0.0")
    .build()
  private val kamonVersion = "0.0.0"
  private val config = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference()).resolve()

  "exporting traces" should {
    "fail in case the remote service is not operable" in {
      val traceService = OtlpTraceService(config)

      // the actual data does not really matter as this will fail due to connection issues
      val resources = SpanConverter.convert(false, resource, kamonVersion)(Seq(finishedSpan()))
      val f = traceService.exportSpans(resources)
      whenReady(f.failed, Timeout(Span.apply(12, Seconds))) { e =>
        e shouldEqual StatusRuntimeException
      }
    }
  }

  "closing service should execute without errors" in {
    OtlpTraceService(config).close()
  }
}
