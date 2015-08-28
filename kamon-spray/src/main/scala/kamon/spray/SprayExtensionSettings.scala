package kamon.spray

import akka.actor.ReflectiveDynamicAccess
import com.typesafe.config.Config

case class SprayExtensionSettings(
  includeTraceTokenHeader: Boolean,
  traceTokenHeaderName: String,
  nameGenerator: NameGenerator,
  clientInstrumentationLevel: ClientInstrumentationLevel.Level)

object SprayExtensionSettings {
  def apply(config: Config): SprayExtensionSettings = {
    val sprayConfig = config.getConfig("kamon.spray")

    val includeTraceTokenHeader: Boolean = sprayConfig.getBoolean("automatic-trace-token-propagation")
    val traceTokenHeaderName: String = sprayConfig.getString("trace-token-header-name")

    val nameGeneratorFQN = sprayConfig.getString("name-generator")
    val nameGenerator: NameGenerator = new ReflectiveDynamicAccess(getClass.getClassLoader)
      .createInstanceFor[NameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.

    val clientInstrumentationLevel: ClientInstrumentationLevel.Level = sprayConfig.getString("client.instrumentation-level") match {
      case "request-level" ⇒ ClientInstrumentationLevel.RequestLevelAPI
      case "host-level"    ⇒ ClientInstrumentationLevel.HostLevelAPI
      case other           ⇒ sys.error(s"Invalid client instrumentation level [$other] found in configuration.")
    }

    SprayExtensionSettings(includeTraceTokenHeader, traceTokenHeaderName, nameGenerator, clientInstrumentationLevel)
  }
}

object ClientInstrumentationLevel {
  sealed trait Level
  case object RequestLevelAPI extends Level
  case object HostLevelAPI extends Level
}
