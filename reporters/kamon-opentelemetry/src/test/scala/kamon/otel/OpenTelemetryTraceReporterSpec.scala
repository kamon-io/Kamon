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
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse}
import kamon.Kamon
import kamon.module.ModuleFactory
import kamon.otel.CustomMatchers.{ByteStringMatchers, KeyValueMatchers, finishedSpan}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}

/**
 * Tests for [[OpenTelemetryTraceReporter]]
 */
class OpenTelemetryTraceReporterSpec extends AnyWordSpec with Matchers with OptionValues with ByteStringMatchers with KeyValueMatchers {

  private def openTelemetryTraceReporter():(OpenTelemetryTraceReporter, MockTraceService) = {
    val traceService = new MockTraceService()
    val reporter = new OpenTelemetryTraceReporter(_ => traceService)(ExecutionContext.global)
    reporter.reconfigure(config)
    (reporter, traceService)
  }

  private val config = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference()).resolve()
  "reporting spans" should {
    "do nothing in case list of spans is empty" in {
      val (reporter, _) = openTelemetryTraceReporter()
      reporter.reportSpans(Nil)
    }

    "convert spans and export them using the TraceService" in {
      val (reporter, traceService) = openTelemetryTraceReporter()
      val span = finishedSpan()
      reporter.reportSpans(Seq(span))
      traceService.exportTraceServiceRequest.value.getResourceSpansCount should be(1)
      val resourceSpans = traceService.exportTraceServiceRequest.value.getResourceSpansList.get(0)
      val kamonVersion = Kamon.status().settings().version
      resourceSpans.getResource.getAttributesList should containStringKV("service.name", "kamon-test-application")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.name", "kamon")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.language", "scala")
      resourceSpans.getResource.getAttributesList should containStringKV("telemetry.sdk.version", kamonVersion)

      //all kamon spans should belong to the same instance of InstrumentationLibrarySpans
      val instSpans = resourceSpans.getInstrumentationLibrarySpansList
      instSpans.size() should be(1)

      //assert instrumentation labels
      val instrumentationLibrarySpans = instSpans.get(0)
      instrumentationLibrarySpans.getInstrumentationLibrary.getName should be("kamon")
      instrumentationLibrarySpans.getInstrumentationLibrary.getVersion should be(kamonVersion)

      //there should be a single span reported
      val spans = instSpans.get(0).getSpansList
      spans.size() should be(1)

      //not doing deeper comparison of the span conversion as these are tested in the SpanConverterSpec
    }
  }

  "stopping reporter should close underlying TraceService" in {
    val (reporter, traceService) = openTelemetryTraceReporter()
    reporter.stop()
    traceService.hasBeenClosed should be(true)
    reporter.stop() //stopping a second time should not matter
  }

  "building instance with the factory should work" in {
    val reporter = new OpenTelemetryTraceReporter.Factory().create(ModuleFactory.Settings(config, ExecutionContext.global))
    reporter.stop()
  }

  private class MockTraceService extends TraceService{
    var exportTraceServiceRequest:Option[ExportTraceServiceRequest] = None
    var hasBeenClosed = false
    override def exportSpans(request: ExportTraceServiceRequest): Future[ExportTraceServiceResponse] = {
      exportTraceServiceRequest = Option(request)
      Future.successful(ExportTraceServiceResponse.getDefaultInstance)
    }
    override def close(): Unit = hasBeenClosed = true
  }
}
