package kamon.spray

import akka.actor.ExtendedActorSystem

case class SprayExtensionSettings(
  includeTraceTokenHeader: Boolean,
  traceTokenHeaderName: String,
  nameGenerator: SprayNameGenerator,
  clientInstrumentationLevel: ClientInstrumentationLevel.Level)

object SprayExtensionSettings {
  def apply(system: ExtendedActorSystem): SprayExtensionSettings = {
    val config = system.settings.config.getConfig("kamon.spray")

    val includeTraceTokenHeader: Boolean = config.getBoolean("automatic-trace-token-propagation")
    val traceTokenHeaderName: String = config.getString("trace-token-header-name")

    val nameGeneratorFQN = config.getString("name-generator")
    val nameGenerator: SprayNameGenerator = system.dynamicAccess.createInstanceFor[SprayNameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.

    val clientInstrumentationLevel: ClientInstrumentationLevel.Level = config.getString("client.instrumentation-level") match {
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
