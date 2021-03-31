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
import io.grpc.StatusRuntimeException
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.InstrumentationLibrary
import io.opentelemetry.proto.resource.v1.Resource
import kamon.otel.CustomMatchers._
import kamon.otel.SpanConverter.stringKeyValue
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}

import java.util.Collections

/**
 * Tests for the [[TraceService]]
 */
class TraceServiceSpec extends WordSpec with Matchers with ScalaFutures {
  private implicit val defaultPatience = PatienceConfig(timeout =  Span(2, Seconds), interval = Span(15, Millis))

  private val resource =  Resource.newBuilder()
    .addAttributes(stringKeyValue("service.name", "TestService"))
    .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
    .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
    .addAttributes(stringKeyValue("telemetry.sdk.version", "0.0.0"))
    .build()
  private val instrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion("0.0.0").build()
  private val config = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference()).resolve()

  "exporting traces" should {
    "fail in case the remote service is not operable" in {
      val traceService = GrpcTraceService(config)

      //the actual data does not really matter as this will fail due to connection issues
      val resources = SpanConverter.toProtoResourceSpan(resource, instrumentationLibrary)(Seq(finishedSpan()))
      val export = ExportTraceServiceRequest.newBuilder()
        .addAllResourceSpans(Collections.singletonList(resources))
        .build()
      val f = traceService.export(export)
      whenReady(f.failed) { e =>
        e shouldBe a [StatusRuntimeException]
      }
    }
  }

  "closing service should execute without errors" in {
    GrpcTraceService(config).close()
  }
}
