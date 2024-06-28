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

package kamon.status.page

import java.lang.{StringBuilder => JavaStringBuilder}

import com.grack.nanojson.{JsonAppendableWriter, JsonWriter}
import com.typesafe.config.ConfigRenderOptions
import kamon.module.Module
import kamon.status.Status
import kamon.tag.{Tag, TagSet}

import scala.compat.Platform.EOL

trait JsonMarshalling[T] {

  /**
    * Implementations should append a Json object or array that describes the given instance members and any
    * additional information that is expected to be shown in the status mini site.
    */
  def toJson(instance: T, builder: JavaStringBuilder): Unit
}

object JsonMarshalling {

  implicit object ModuleRegistryStatusJsonMarshalling extends JsonMarshalling[Status.ModuleRegistry] {
    override def toJson(instance: Status.ModuleRegistry, builder: JavaStringBuilder): Unit = {
      def moduleKindString(moduleKind: Module.Kind): String = moduleKind match {
        case Module.Kind.CombinedReporter => "combined"
        case Module.Kind.MetricsReporter  => "metric"
        case Module.Kind.SpansReporter    => "span"
        case Module.Kind.ScheduledAction  => "scheduled"
        case Module.Kind.Unknown          => "unknown"
      }

      val array = JsonWriter.on(builder)
        .`object`()
        .array("modules")

      instance.modules.foreach(m => {
        array.`object`()
          .value("name", m.name)
          .value("description", m.description)
          .value("clazz", m.clazz)
          .value("kind", moduleKindString(m.kind))
          .value("programmaticallyRegistered", m.programmaticallyRegistered)
          .value("enabled", m.enabled)
          .value("started", m.started)
          .end()
      })

      array.end().end().done()
    }
  }

  implicit object BaseInfoJsonMarshalling extends JsonMarshalling[Status.Settings] {
    override def toJson(instance: Status.Settings, builder: JavaStringBuilder): Unit = {
      val baseConfigJson = JsonWriter.on(builder)
        .`object`()
        .value("version", instance.version)
        .value("config", instance.config.root().render(ConfigRenderOptions.concise()))

      baseConfigJson.`object`("environment")
        .value("service", instance.environment.service)
        .value("host", instance.environment.host)
        .value("instance", instance.environment.instance)
        .`object`("tags")

      instance.environment.tags.iterator(_.toString).foreach { pair =>
        baseConfigJson.value(pair.key, pair.value)
      }

      baseConfigJson
        .end() // ends tags
        .end() // ends environment
        .end() // ends base config
        .done()
    }
  }

  implicit object MetricRegistryStatusJsonMarshalling extends JsonMarshalling[Status.MetricRegistry] {
    override def toJson(instance: Status.MetricRegistry, builder: JavaStringBuilder): Unit = {
      val metricsObject = JsonWriter.on(builder)
        .`object`
        .array("metrics")

      instance.metrics.foreach(metric => {
        metricsObject
          .`object`()
          .value("name", metric.name)
          .value("description", metric.description)
          .value("type", metric.instrumentType.name)
          .value("unitDimension", metric.unit.dimension.name)
          .value("unitMagnitude", metric.unit.magnitude.name)
          .value("instrumentType", metric.instrumentType.name)

        val instrumentsArray = metricsObject.array("instruments")
        metric.instruments.foreach(i => tagSetToJson(i.tags, instrumentsArray))

        metricsObject
          .end() // instruments
          .end() // metric info
      })

      metricsObject
        .end() // metrics array
        .end() // object
        .done()
    }
  }

  private def tagSetToJson(tags: TagSet, writer: JsonAppendableWriter): Unit = {
    val tagsObject = writer.`object`()
    tags.iterator().foreach(t => tagsObject.value(t.key, Tag.unwrapValue(t).toString))
    tagsObject.end()
  }

  implicit object InstrumentationStatusJsonMarshalling extends JsonMarshalling[Status.Instrumentation] {
    override def toJson(instance: Status.Instrumentation, builder: JavaStringBuilder): Unit = {
      val instrumentationObject = JsonWriter.on(builder)
        .`object`()
        .value("present", instance.present)
        .`object`("modules")

      instance.modules.foreach { module =>
        instrumentationObject.`object`(module.path)
          .value("name", module.name)
          .value("description", module.description)
          .value("enabled", module.enabled)
          .value("active", module.active)
          .end()
      }

      instrumentationObject
        .end() // end modules
        .`object`("errors")

      instance.errors.foreach { typeError =>
        val errorsArray = instrumentationObject.array(typeError.targetType)
        typeError.errors.foreach(t => {
          errorsArray.`object`()
            .value("message", t.getMessage)
            .value("stacktrace", t.getStackTrace.mkString("", EOL, EOL))
            .end()
        })
        errorsArray.end()
      }

      instrumentationObject
        .end() // errors
        .end() // object
        .done()
    }
  }
}
