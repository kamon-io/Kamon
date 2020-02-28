/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic.spans

import java.net.InetAddress

import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.spans.{SpanBatch, SpanBatchSender, Span => NewRelicSpan}
import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory}
import kamon.Kamon
import kamon.status.Environment
import kamon.tag.TagSet
import kamon.trace.Span
import kamon.trace.Span.Kind.Client
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters._

class NewRelicSpanReporterSpec extends WordSpec with Matchers {

  "the span reporter" should {
    "report spans" in {
      val kamonSpan = TestSpanHelper.makeKamonSpan(Client, TestSpanHelper.spanId)
      val spans: Seq[Span.Finished] = Seq(kamonSpan)
      val expectedBatch: SpanBatch = buildExpectedBatch()

      val builder = mock(classOf[SpanBatchSenderBuilder])
      val sender = mock(classOf[SpanBatchSender])

      when(builder.build(any[Config])).thenReturn(sender)

      val reporter = new NewRelicSpanReporter(builder)
      reporter.reportSpans(spans)
      verify(sender).sendBatch(expectedBatch);
    }

    "be reconfigurable" in {
      val builder = mock(classOf[SpanBatchSenderBuilder])
      val sender = mock(classOf[SpanBatchSender])

      when(builder.build(any[Config])).thenReturn(sender)

      val reporter = new NewRelicSpanReporter(builder)
      //change the service name attribute and tags and make sure that we reconfigure with it!
      val tagDetails = ConfigValueFactory.fromMap(Map("testTag" -> "testThing").asJava)
      val configObject: ConfigValue = ConfigValueFactory.fromMap(Map("service" -> "cheese-whiz", "host" -> "thing", "tags" -> tagDetails).asJava)
      val config: Config = Kamon.config().withValue("kamon.environment", configObject)
      reporter.reconfigure(config, Environment("thing", "cheese-whiz", null, null, TagSet.of("testTag", "testThing")))

      val kamonSpan = TestSpanHelper.makeKamonSpan(Client, TestSpanHelper.spanId)
      val spans: Seq[Span.Finished] = Seq(kamonSpan)
      val expectedBatch: SpanBatch = buildExpectedBatch("cheese-whiz", "thing", "testThing")

      reporter.reportSpans(spans)
      verify(sender).sendBatch(expectedBatch);
    }
  }

  private def buildExpectedBatch(serviceName: String = "kamon-application", hostName : String = InetAddress.getLocalHost.getHostName, tagValue: String = "testValue" ) = {
    val expectedAttributes = new Attributes()
      .put("xx", TestSpanHelper.now)
      .put("span.kind", "client")
      .put("foo", "bar")
    val expectedSpan = NewRelicSpan.builder(TestSpanHelper.spanId)
      .name(TestSpanHelper.name)
      .traceId(TestSpanHelper.traceId)
      .timestamp(TestSpanHelper.before)
      .durationMs(1000)
      .attributes(expectedAttributes)
      .parentId(TestSpanHelper.parentId)
      .build()
    val commonAttributes = new Attributes()
      .put("instrumentation.provider", "kamon-agent")
      .put("service.name", serviceName)
      .put("host", hostName)
      .put("testTag", tagValue)
    val expectedSpans = List(expectedSpan).asJava
    new SpanBatch(expectedSpans, commonAttributes)
  }
}
