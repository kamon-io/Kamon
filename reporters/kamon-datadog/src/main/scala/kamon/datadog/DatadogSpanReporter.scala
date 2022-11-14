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

package kamon.datadog

import java.time.Duration

import com.grack.nanojson.{JsonArray, JsonObject, JsonWriter}
import com.typesafe.config.Config
import kamon.trace.Span
import kamon.{ ClassLoading, Kamon }
import kamon.datadog.DatadogSpanReporter.Configuration
import kamon.module.{ ModuleFactory, SpanReporter }
import kamon.tag.{ Lookups, Tag, TagSet }
import kamon.trace.Span.TagKeys
import kamon.util.{ EnvironmentTags, Filter }
import org.slf4j.LoggerFactory

import scala.util.{ Failure }

trait KamonDataDogTranslator {
  def translate(span: Span.Finished, additionalTags: TagSet, tagFilter: Filter): DdSpan
}

object KamonDataDogTranslatorDefault extends KamonDataDogTranslator {
  def translate(span: Span.Finished, additionalTags: TagSet, tagFilter: Filter): DdSpan = {
    // DataDog only accepts 64-bit trace IDs, and its agent will use the lower 64 bits when presented with a 128-bit ID.
    // Do the same truncation here for compatibility.
    val traceId = BigInt(span.trace.id.string.takeRight(16), 16)
    val spanId = BigInt(span.id.string, 16)

    val parentId = if (span.parentId.isEmpty) None else Some(BigInt(span.parentId.string, 16))
    val name = span.metricTags
      .get(Lookups.option("component"))
      .getOrElse("kamon.trace")

    val resource = span.operationName
    val service = Kamon.environment.service
    val from = span.from
    val start = from.getEpochNano
    val duration = Duration.between(from, span.to)
    val marks = span.marks.map { m => m.key -> m.instant.getEpochNano.toString }.toMap
    val errorTags = if (span.hasError) {
      val builder = TagSet.builder()
      span.tags.get(Lookups.option(TagKeys.ErrorMessage)).foreach(msg => builder.add("error.msg", msg))
      span.tags.get(Lookups.option(TagKeys.ErrorStacktrace)).foreach(st => builder.add("error.stack", st))
      builder.build()
    } else TagSet.Empty

    val tags = (span.tags.all() ++ span.metricTags.all() ++ errorTags.all() ++ additionalTags.all()).map { t =>
      t.key -> Tag.unwrapValue(t).toString
    }
    val meta = (marks ++ tags).filterKeys(tagFilter.accept(_)).toMap
    new DdSpan(traceId, spanId, parentId, name, resource, service, "custom", start, duration, meta, span.hasError)

  }
}

object DatadogSpanReporter {

  case class Configuration(
    translator: KamonDataDogTranslator,
    httpClient: HttpClient,
    tagFilter:  Filter,
    envTags:    TagSet
  )

  private[kamon] val httpConfigPath = "kamon.datadog.trace"

  private[kamon] def getTranslator(config: Config): KamonDataDogTranslator = {
    config.getConfig(httpConfigPath).getString("translator") match {
      case "default" => KamonDataDogTranslatorDefault
      case fqn       => ClassLoading.createInstance[KamonDataDogTranslator](fqn)
    }
  }

  def getConfiguration(config: Config) = {

    Configuration(
      getTranslator(config),
      new HttpClient(config.getConfig(DatadogSpanReporter.httpConfigPath), usingAgent = true),
      Kamon.filter("kamon.datadog.environment-tags.filter"),
      EnvironmentTags.from(Kamon.environment, config.getConfig("kamon.datadog.environment-tags")).without("service")
    )
  }
}

class DatadogSpanReporterFactory extends ModuleFactory {
  override def create(settings: ModuleFactory.Settings): DatadogSpanReporter = {
    new DatadogSpanReporter(
      DatadogSpanReporter.getConfiguration(settings.config)
    )
  }
}

class DatadogSpanReporter(@volatile private var configuration: Configuration) extends SpanReporter {

  private val logger = LoggerFactory.getLogger(classOf[DatadogSpanReporter])

  override def reportSpans(spans: Seq[Span.Finished]): Unit = if (spans.nonEmpty) {
    val spanLists: Array[Array[JsonObject]] = spans
      .map(span => configuration.translator.translate(span, configuration.envTags, configuration.tagFilter).toJson())
      .groupBy { jsonObj => Option(jsonObj.getNumber("trace_id")).get.toString() }
      .mapValues(_.toArray)
      .values
      .toArray

    val jsonBuilder = JsonArray.builder
    spanLists.foreach { span =>
      jsonBuilder.value(span)
    }

    configuration.httpClient.doJsonPut(JsonWriter.string(jsonBuilder.done)) match {
      case Failure(exception) =>
        throw exception
      case _ => ()
    }
  }

  logger.info("Started the Kamon DataDog span reporter")

  override def stop(): Unit = {
    logger.info("Stopped the Kamon DataDog span reporter")
  }

  override def reconfigure(config: Config): Unit = {
    logger.info("Reconfigured the Kamon DataDog span reporter")
    configuration = DatadogSpanReporter.getConfiguration(config)
  }

}
