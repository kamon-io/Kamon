/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.newrelic.spans

import com.newrelic.telemetry.spans.SpanBatch
import com.typesafe.config.Config
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.newrelic.AttributeBuddy.buildCommonAttributes
import kamon.status.Environment
import kamon.trace.Span
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class NewRelicSpanReporter(spanBatchSenderBuilder: SpanBatchSenderBuilder =
  new SimpleSpanBatchSenderBuilder()) extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[NewRelicSpanReporter])
  @volatile private var spanBatchSender = spanBatchSenderBuilder.build(Kamon.config())
  @volatile private var commonAttributes = buildCommonAttributes(Kamon.environment)

  checkJoinParameter()
  logger.info("Started the New Relic Span reporter")

  //   TODO is this actually needed with NR Telemetry SDK? research exactly what this does
  def checkJoinParameter(): Unit = {
    val joinRemoteParentsWithSameID = Kamon.config().getBoolean("kamon.trace.join-remote-parents-with-same-span-id")
    if (!joinRemoteParentsWithSameID) {
      logger.warn(
        "For full distributed trace compatibility enable `kamon.trace.join-remote-parents-with-same-span-id` to " +
        "preserve span id across client/server sides of a Span."
      )
    }
  }

  /**
   * Sends batches of Spans to New Relic using the Telemetry SDK
   *
   * Modules implementing the SpanReporter trait will get registered for periodically receiving span batches. The frequency of the
   * span batches is controlled by the kamon.trace.tick-interval setting.
   *
   * @param spans - spans to report to New Relic
   */
  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    logger.debug("NewRelicSpanReporter reportSpans...")
    val newRelicSpans = spans.map(NewRelicSpanConverter.convertSpan).asJava
    spanBatchSender.sendBatch(new SpanBatch(newRelicSpans, commonAttributes))
  }

  override def reconfigure(newConfig: Config): Unit = {
    reconfigure(newConfig, Kamon.environment)
  }

  // exposed for testing
  def reconfigure(newConfig: Config, environment: Environment): Unit = {
    logger.debug("NewRelicSpanReporter reconfigure...")
    spanBatchSender = spanBatchSenderBuilder.build(newConfig)
    commonAttributes = buildCommonAttributes(environment)
    checkJoinParameter()
  }

  override def stop(): Unit =
    logger.info("Stopped the New Relic Span reporter")
}

object NewRelicSpanReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new NewRelicSpanReporter()
  }

}
