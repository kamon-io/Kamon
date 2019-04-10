package kamon.status.page

import java.lang.{StringBuilder => JavaStringBuilder}

import com.grack.nanojson.{JsonAppendableWriter, JsonWriter}
import com.typesafe.config.ConfigRenderOptions
import kamon.module.Module
import kamon.status.Status
import kamon.tag.{Tag, TagSet}

import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsScalaMapConverter}


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
        case Module.Kind.Combined => "combined"
        case Module.Kind.Metric   => "metric"
        case Module.Kind.Span     => "span"
        case Module.Kind.Plain    => "plain"
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
          .value("active", instance.active)
          .`object`("modules")

      instance.modules.asScala.foreach {
        case (moduleName, moduleDescription) => instrumentationObject.value(moduleName, moduleDescription)
      }

      instrumentationObject
        .end() // end modules
        .`object`("errors")

      instance.errors.asScala.foreach {
        case (moduleName, errors) =>
          instrumentationObject.array(moduleName)
          errors.asScala.foreach(t => instrumentationObject.value(t.toString))
          instrumentationObject.end()
      }

      instrumentationObject
        .end() // errors
        .end() // object
        .done()
    }
  }
}