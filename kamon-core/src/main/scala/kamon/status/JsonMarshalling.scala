package kamon.status

import com.grack.nanojson.JsonWriter
import kamon.module.Module
import kamon.module.Module.Registry
import java.lang.{StringBuilder => JavaStringBuilder}

import com.typesafe.config.ConfigRenderOptions


trait JsonMarshalling[T] {

  /**
    * Implementations should append a Json object or array that describes the given instance members and any
    * additional information that is expected to be shown in the status mini site.
    */
  def toJson(instance: T, builder: JavaStringBuilder): Unit
}

object JsonMarshalling {

  implicit object ModuleRegistryStatusJsonMarshalling extends JsonMarshalling[Module.Registry.Status] {
    override def toJson(instance: Registry.Status, builder: JavaStringBuilder): Unit = {
      val array = JsonWriter.on(builder)
        .`object`()
        .array("modules")

      instance.modules.foreach(m => {
        array.`object`()
          .value("name", m.name)
          .value("description", m.description)
          .value("enabled", m.enabled)
          .value("started", m.started)
          .end()
      })

      array.end().end().done()
    }
  }

  implicit object BaseInfoJsonMarshalling extends JsonMarshalling[Status.BaseInfo] {
    override def toJson(instance: Status.BaseInfo, builder: JavaStringBuilder): Unit = {
      val baseConfigJson = JsonWriter.on(builder)
        .`object`()
          .value("version", instance.version)
          .value("config", instance.config.root().render(ConfigRenderOptions.concise()))

      baseConfigJson.`object`("environment")
        .value("service", instance.environment.service)
        .value("host", instance.environment.host)
        .value("instance", instance.environment.instance)
        .`object`("tags")

      instance.environment.tags.foreach {
        case (key, value) => baseConfigJson.value(key, value)
      }

      baseConfigJson
        .end() // ends tags
        .end() // ends environment
        .end() // ends base config
        .done()
    }
  }
}