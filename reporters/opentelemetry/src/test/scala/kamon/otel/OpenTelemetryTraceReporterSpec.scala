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

import java.util.{Collection => JCollection}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.Kamon
import kamon.module.ModuleFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * Tests for [[OpenTelemetryTraceReporter]]
  */
class OpenTelemetryTraceReporterSpec extends AnyWordSpec with Matchers with OptionValues with Utils {

  private def openTelemetryTraceReporter(): (OpenTelemetryTraceReporter, MockTraceService) = {
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
      // there should be a single span reported
      traceService.exportTraceServiceRequest.value.size() shouldEqual 1
      val spanData = traceService.exportTraceServiceRequest.value.iterator().next()
      val kamonVersion = Kamon.status().settings().version
      spanData.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("service.name"),
        "kamon-test-application"
      )
      spanData.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.name"),
        "kamon"
      )
      spanData.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.language"),
        "scala"
      )
      spanData.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("telemetry.sdk.version"),
        kamonVersion
      )
      spanData.getResource.getAttributes.asMap().asScala should contain(
        AttributeKey.stringKey("service.version"),
        "x.x.x"
      )
      spanData.getResource.getAttributes.asMap().asScala should contain(AttributeKey.stringKey("env"), "kamon-devint")
      spanData.getResource.getAttributes.asMap().asScala should contain(AttributeKey.stringKey("att1"), "v1")
      spanData.getResource.getAttributes.asMap().asScala should contain(AttributeKey.stringKey("att2"), "v2")
      spanData.getResource.getAttributes.asMap().asScala should contain(AttributeKey.stringKey("att3"), " a=b,c=d ")
      val host = spanData.getResource.getAttributes.asMap().asScala.get(AttributeKey.stringKey("host.name"))
      host shouldBe defined
      val instance =
        spanData.getResource.getAttributes.asMap().asScala.get(AttributeKey.stringKey("service.instance.id"))
      instance should contain(s"kamon-test-application@${host.get}")

      // assert instrumentation labels
      val instrumentationScopeInfo = spanData.getInstrumentationScopeInfo
      instrumentationScopeInfo.getName should be("kamon-instrumentation")
      instrumentationScopeInfo.getVersion should be(kamonVersion)
      instrumentationScopeInfo.getSchemaUrl should be(null)
      // deprecated
      val instrumentationLibraryInfo = spanData.getInstrumentationLibraryInfo
      instrumentationLibraryInfo.getName should be("kamon-instrumentation")
      instrumentationLibraryInfo.getVersion should be(kamonVersion)
    }
  }

  "stopping reporter should close underlying TraceService" in {
    val (reporter, traceService) = openTelemetryTraceReporter()
    reporter.stop()
    traceService.hasBeenClosed should be(true)
    reporter.stop() // stopping a second time should not matter
  }

  "building instance with the factory should work" in {
    val reporter =
      new OpenTelemetryTraceReporter.Factory().create(ModuleFactory.Settings(config, ExecutionContext.global))
    reporter.stop()
  }

  private class MockTraceService extends TraceService {
    var exportTraceServiceRequest: Option[JCollection[SpanData]] = None
    var hasBeenClosed = false

    override def exportSpans(spans: JCollection[SpanData]): Future[Unit] = {
      exportTraceServiceRequest = Option(spans)
      Future.successful(())
    }

    override def close(): Unit = hasBeenClosed = true
  }
}
